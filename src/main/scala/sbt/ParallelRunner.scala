/* sbt -- Simple Build Tool
 * Copyright 2009  Mark Harrah
 */
package sbt

/** This file provides the parallel execution engine of sbt.  It is a fairly general module, with pluggable Schedulers and Strategies.
*
* There are three main componenets to the engine: Distributors, Schedulers, and Strategies.
*
* A Scheduler provides work that is ready to execute.  The main type of Scheduler in sbt is a scheduler
* of nodes in a directed, acyclic graph..  This type of scheduler provides work when its
* dependencies have finished executing successfully.  Another type of scheduler is a MultiScheduler, which draws work
* from sub-schedulers.
*
* A Strategy is used by a Scheduler to select the work to process from the work that is ready.  It is notified as work
* becomes ready.  It is requested to select work to process from the work that is ready.  The main Strategy in sbt is the
* OrderedStrategy, which prioritizes work according to some ordering defined by its constructor.  The primary ordering
* used in sbt is based on the longest length of the processing path that includes the node being ordered.
*
* A Distributor uses a Scheduler to obtain work according up to the maximum work allowed to run at once.  It runs each
* unit of work in its own Thread.
**/

import java.util.concurrent.LinkedBlockingQueue
import scala.collection.{immutable, mutable}
import immutable.TreeSet

/** Interface to the Distributor/Scheduler system for running tasks with dependencies described by a directed acyclic graph.*/
object ParallelRunner
{
	/** Executes work for nodes in an acyclic directed graph with root node `node`.  The name of a node is provided
	* by the `name` function, the work to perform for a node by `action`, and the logger to use for a node by `log`.
	* The maximum number of tasks to execute simultaneously is `maximumTasks`. */
	def run[D <: Dag[D]](node: D, name: D => String, action: D => Option[String], maximumTasks: Int, log: D => Logger): List[WorkFailure[D]] =
	{
		val info = DagInfo(node)
		// Create a strategy that gives each node a uniform self cost and uses the maximum cost to execute it and the nodes that depend on it
		// to determine which node to run.  The self cost could be modified to include more information about a node, such as the size of input files
		val strategy = defaultStrategy(info)
		val jobScheduler = CompoundScheduler(new DagScheduler(info, strategy), strategy)
		val distributor = new Distributor(jobScheduler, action, maximumTasks, log)
		val result = distributor.run().toList
		for( WorkFailure(work, message) <- result ) yield WorkFailure(work, "Error running " + name(work) + ": " + message)
	}
	def dagScheduler[D <: Dag[D]](node: D) =
	{
		val info = DagInfo(node)
		new DagScheduler(info, defaultStrategy(info))
	}
	private def defaultStrategy[D <: Dag[D]](info: DagInfo[D]) = MaxPathStrategy((d: D) => 1, info)
}
/** Requests work from `scheduler` and processes it using `doWork`.  This class limits the amount of work processing at any given time
* to `workers`.*/
final class Distributor[D](scheduler: Scheduler[D], doWork: D => Option[String], workers: Int, log: D => Logger) extends NotNull
{
	require(workers > 0)
	final def run(): Iterable[WorkFailure[D]]  = (new Run).run()

	private final class Run extends NotNull
	{
		private[this] val schedule = scheduler.run
		/** The number of threads currently running. */
		private[this] var running = 0
		/** Pending notifications of completed work. */
		private[this] val complete = new java.util.concurrent.LinkedBlockingQueue[Done]
		
		private[Distributor] def run(): Iterable[WorkFailure[D]] =
		{
			next()
			if(isIdle && !schedule.hasPending) // test if all work is complete
				schedule.failures
			else
			{
				waitForCompletedWork() // wait for some work to complete 
				run() // continue
			}
		}
		// true if the maximum number of worker threads are currently running
		private def atMaximum = running == workers
		private def availableWorkers = workers - running
		// true if no worker threads are currently running
		private def isIdle = running == 0
		// process more work
		private def next()
		{
			 // if the maximum threads are being used, do nothing
			 // if all work is complete or the scheduler is waiting for current work to complete, do nothing
			if(!atMaximum && schedule.hasPending)
			{
				val nextWork = schedule.next(availableWorkers)
				val nextSize = nextWork.size
				assume(nextSize <= availableWorkers, "Scheduler provided more work (" + nextSize + ") than allowed (" + availableWorkers + ")")
				assume(nextSize > 0 || !isIdle, "Distributor idle and the scheduler indicated work pending, but provided no work.")
				nextWork.foreach(process)
			}
		}
		// wait on the blocking queue `complete` until some work finishes and notify the scheduler
		private def waitForCompletedWork()
		{
			require(running > 0)
			val done = complete.take()
			running -= 1
			schedule.complete(done.data, done.result)
		}
		private def process(data: D)
		{
			require(running + 1 <= workers)
			running += 1
			new Worker(data).start()
		}
		private class Worker(data: D) extends Thread with NotNull
		{
			override def run()
			{
				val result = Control.trapUnit("", log(data))(doWork(data))
				complete.put( new Done(result, data) )
			}
		}
	}
	private final class Done(val result: Option[String], val data: D) extends NotNull
}
final case class WorkFailure[D](work: D, message: String) extends NotNull
{
	override def toString = message
}
/** Schedules work of type D.  A Scheduler determines what work is ready to be processed.
* A Scheduler is itself immutable.  It creates a mutable object for each scheduler run.*/
trait Scheduler[D] extends NotNull
{
	/** Starts a new run.  The returned object is a new Run, representing a single scheduler run.  All state for the run
	* is encapsulated in this object.*/
	def run: Run
	trait Run extends NotNull
	{
		/** Notifies this scheduler that work has completed with the given result (Some with the error message or None if the work succeeded).*/
		def complete(d: D, result: Option[String]): Unit
		/** Returns true if there is any more work to be done, although remaining work can be blocked
		* waiting for currently running work to complete.*/
		def hasPending: Boolean
		/**Returns true if this scheduler has no more work to be done, ever.*/
		def isComplete: Boolean
		/** Returns up to 'max' units of work.  `max` is always positive.  The returned sequence cannot be empty if there is
		* no work currently being processed.*/
		def next(max: Int): Seq[D]
		/** A list of failures that occurred to this point, as reported to the `complete` method. */
		def failures: Iterable[WorkFailure[D]]
	}
}
/** A Strategy selects the work to process from work that is ready to be processed.*/
private trait ScheduleStrategy[D] extends NotNull
{
	/** Starts a new run.  The returned object is a new Run, representing a single strategy run.  All state for the run
	* is handled through this object and is encapsulated in this object.*/
	def run: Run
	trait Run extends NotNull
	{
		/** Adds the given work to the list of work that is ready to run.*/
		def workReady(dep: D): Unit
		/** Returns true if there is work ready to be run. */
		def hasReady: Boolean
		/** Provides up to `max` units of work.  `max` is always positive and this method is not called
		* if hasReady is false. The returned list cannot be empty is there is work ready to be run.*/
		def next(max: Int): List[D]
	}
}

/** A scheduler for nodes of a directed-acyclic graph.  It requires the root of the graph
* and a strategy to select which available nodes to run on limited resources.*/
private[sbt] final class DagScheduler[D <: Dag[D]](info: DagInfo[D], strategy: ScheduleStrategy[D]) extends Scheduler[D]
{
	def run: Run = new Run
	{
		val infoRun = info.run
		val strategyRun = strategy.run
		
		// find nodes that are ready to be run (no dependencies)
		{
			val startReady = for( (key, value) <- infoRun.remainingDepsRun if(value.isEmpty)) yield key
			infoRun.remainingDepsRun --= startReady
			startReady.foreach(strategyRun.workReady)
		}
			
		val failures = new mutable.ListBuffer[WorkFailure[D]]
		def next(max: Int) = strategyRun.next(max)
		def complete(work: D, result: Option[String])
		{
			result match
			{
				case None => infoRun.complete(work, strategyRun.workReady)
				case Some(errorMessage) =>
					infoRun.clear(work)
					failures += WorkFailure(work, errorMessage)
			}
		}
		def isComplete = !strategyRun.hasReady && infoRun.reverseDepsRun.isEmpty
		// the strategy might not have any work ready if the remaining work needs currently executing work to finish first
		def hasPending = strategyRun.hasReady || !infoRun.remainingDepsRun.isEmpty
	}
}
private object MaxPathStrategy
{
	def apply[D <: Dag[D]](selfCost: D => Int, info: DagInfo[D]): ScheduleStrategy[D] =
	{
		val cost = // compute the cost of the longest execution path ending at each node
		{
			val cost = new mutable.HashMap[D, Int]
			def computeCost(work: D): Int = info.reverseDeps.getOrElse(work, immutable.Set.empty[D]).foldLeft(0)(_ max getCost(_)) + selfCost(work)
			def getCost(work: D): Int = cost.getOrElseUpdate(work, computeCost(work))
			info.remainingDeps.keys.foreach(getCost)
			wrap.Wrappers.readOnly(cost)
		}
		// create a function to compare units of work.  This is not as simple as cost(a) compare cost(b) because it cannot return 0 for
		// unequal nodes (at least for the Ordered comparison)
		
		// 2.8.0 uses Ordering
		implicit val compareOrdering: Ordering[D] =
			new Ordering[D]
			{
				def compare(a: D, b: D) =
				{
					val base = cost(a) compare cost(b)
					if(base == 0)
						a.hashCode compare b.hashCode // this is required because TreeSet interprets 0 as equal
					else
						base
				}
			}
		// 2.7.x uses an implicit view to Ordered
		implicit val compare =
			(a: D) => new Ordered[D] {
				def compare(b: D) = compareOrdering.compare(a, b)
			}
		new OrderedStrategy(new TreeSet())
	}
}
/** A strategy that adds work to a tree and selects the last key as the next work to be done. */
private class OrderedStrategy[D](ready: TreeSet[D]) extends ScheduleStrategy[D]
{
	def run = new Run
	{
		private[this] var readyRun = ready
		def next(max: Int): List[D] = nextImpl(max, Nil)
		private[this] def nextImpl(remaining: Int, accumulated: List[D]): List[D] =
		{
			if(remaining <= 0 || readyRun.isEmpty)
				accumulated
			else
			{
				val next = readyRun.lastKey
				readyRun -= next
				nextImpl(remaining - 1, next :: accumulated)
			}
		}
		def workReady(dep: D) { readyRun += dep }
		def hasReady = !readyRun.isEmpty
	}
}
/** A class that represents state for a DagScheduler and that MaxPathStrategy uses to initialize an OrderedStrategy. */
private final class DagInfo[D <: Dag[D]](val remainingDeps: immutable.Map[D, immutable.Set[D]],
	val reverseDeps: immutable.Map[D, immutable.Set[D]]) extends NotNull
{
	def run = new Run
	final class Run extends NotNull
	{
		val remainingDepsRun = DagInfo.mutableMap(remainingDeps)
		val reverseDepsRun = DagInfo.mutableMap(reverseDeps)
		/** Called when work does not complete successfully and so all work that (transitively) depends on the work 
		* must be removed from the maps. */
		def clear(work: D)
		{
			remainingDepsRun -= work
			foreachReverseDep(work)(clear)
		}
		/** Called when work completes properly.  `initial` and `ready` are used for a fold over
		* the work that is now ready to go (becaues it was only waiting for `work` to complete).*/
		def complete(work: D, ready: D => Unit)
		{
			def completed(dependsOnCompleted: D)
			{
				for(remainingDependencies <- remainingDepsRun.get(dependsOnCompleted))
				{
					remainingDependencies -= work
					if(remainingDependencies.isEmpty)
					{
						remainingDepsRun -= dependsOnCompleted
						ready(dependsOnCompleted)
					}
				}
			}
			foreachReverseDep(work)(completed)
		}
		private def foreachReverseDep(work: D)(f: D => Unit) { reverseDepsRun.removeKey(work).foreach(_.foreach(f)) }
	}
}
/** Constructs forward and reverse dependency map for the given Dag root node. */
private object DagInfo
{
	/** Constructs the reverse dependency map from the given Dag and
	* puts the forward dependencies into a map */
	def apply[D <: Dag[D]](root: D): DagInfo[D] =
	{
		val remainingDeps = new mutable.HashMap[D, immutable.Set[D]]
		val reverseDeps = new mutable.HashMap[D, mutable.Set[D]]
		def visitIfUnvisited(node: D): Unit = remainingDeps.getOrElseUpdate(node, processDependencies(node))
		def processDependencies(node: D): Set[D] =
		{
			val workDependencies = node.dependencies
			workDependencies.foreach(visitIfUnvisited)
			for(dep <- workDependencies)
				reverseDeps.getOrElseUpdate(dep, new mutable.HashSet[D]) += node
			immutable.HashSet(workDependencies.toSeq: _*)
		}
		visitIfUnvisited(root)
		new DagInfo(immutable.HashMap(remainingDeps.toSeq : _*), immute(reverseDeps) )
	}
	/** Convert a mutable Map with mutable Sets for values to an immutable Map with immutable Sets for values. */
	private def immute[D](map: mutable.Map[D, mutable.Set[D]]): immutable.Map[D, immutable.Set[D]] =
	{
		val immutedSets = map.map { case (key, value) =>(key,  immutable.HashSet(value.toSeq : _*)) }
		immutable.HashMap(immutedSets.toSeq :_*)
	}
	/** Convert an immutable Map with immutable Sets for values to a mutable Map with mutable Sets for values. */
	private def mutableMap[D](map: immutable.Map[D, immutable.Set[D]]): mutable.Map[D, mutable.Set[D]] =
	{
		val mutableSets = map.map { case (key, value) =>(key,  mutable.HashSet(value.toSeq : _*)) }
		mutable.HashMap(mutableSets.toSeq :_*)
	}
}
/** A scheduler that can get work from sub-schedulers.  The `schedulers` argument to the constructor
* is a sequence of the initial schedulers and the key to provide to a client that uses the 'detailedComplete'
* method when the scheduler completes its work.*/
private final class MultiScheduler[D, T](schedulers: (Scheduler[D], T)*) extends Scheduler[D]
{
	/** Returns a Run instance that represents a scheduler run.*/
	def run = new MultiRun
	final class MultiRun extends Run
	{
		val owners = new mutable.HashMap[D, Scheduler[D]#Run]
		val failures = new mutable.ListBuffer[WorkFailure[D]]
		val schedules = mutable.HashMap[Scheduler[D]#Run, T](schedulers.map { case (scheduler, completeKey) => (scheduler.run, completeKey)} : _*)
		def +=(schedule: Scheduler[D]#Run, completeKey: T) { schedules(schedule) = completeKey }
	
		def isComplete = schedules.keys.forall(_.isComplete)
		def hasPending = schedules.keys.exists(_.hasPending)
		def next(max: Int) = nextImpl(max, schedules.keys.toList, Nil)
	
		private def nextImpl(max: Int, remaining: List[Scheduler[D]#Run], accumulatedWork: List[D]): Seq[D] =
		{
			if(max == 0 || remaining.isEmpty)
				accumulatedWork
			else
			{
				val currentSchedule = remaining.head
				if(currentSchedule.hasPending)
				{
					val newWork = currentSchedule.next(max).toList
					newWork.foreach(work => owners.put(work, currentSchedule))
					nextImpl(max - newWork.size, remaining.tail, newWork ::: accumulatedWork)
				}
				else
					nextImpl(max, remaining.tail, accumulatedWork)
			}
		}
	
		def complete(work: D, result: Option[String]) { detailedComplete(work, result) }
		def detailedComplete(work: D, result: Option[String]) =
		{
			def complete(forOwner: Scheduler[D]#Run) =
			{
				forOwner.complete(work, result)
				if(forOwner.isComplete)
				{
					failures ++= forOwner.failures
					Some(forOwner, schedules.removeKey(forOwner).get)
				}
				else
					None
			}
			owners.removeKey(work).flatMap(complete)
		}
	}
}
/** This scheduler allows a unit of work to provide nested work.
*
* When a unit of work that implements CompoundWork is returned for processing by `multi`, this scheduler will request the work's
* nested scheduler that represents the nested work to be done.  The new scheduler will be added to `multi`.  When the new scheduler
* is finished providing work, a final scheduler is run.*/
private final class CompoundScheduler[D](multi: MultiScheduler[D, Option[FinalWork[D]]], finalWorkStrategy: ScheduleStrategy[D]) extends Scheduler[D]
{
	def run: Run = new Run
	{
		val multiRun = multi.run
		val strategyRun = finalWorkStrategy.run
	
		def isComplete = multiRun.isComplete && !strategyRun.hasReady
		def hasPending = strategyRun.hasReady || multiRun.hasPending || multiRun.schedules.values.exists(_.isDefined)
		def complete(work: D, result: Option[String]) =
		{
			for( (scheduler, Some(finalWorkTodo)) <- multiRun.detailedComplete(work, result) )
			{
				multiRun += (finalWorkTodo.doFinally.run, None)
				if(scheduler.failures.isEmpty)
					strategyRun workReady finalWorkTodo.compound
				else
					multiRun.complete(finalWorkTodo.compound, Some("One or more subtasks failed"))
			}
		}
		def failures = multiRun.failures
		def next(max: Int) = nextImpl(max, Nil)
		private def nextImpl(max: Int, processedNextWork: List[D]): Seq[D] =
		{
			if(max > 0)
			{
				if(strategyRun.hasReady)
				{
					val newWork = strategyRun.next(max)
					nextImpl(max - newWork.size, newWork ::: processedNextWork)
				}
				else if(multiRun.hasPending)
				{
					val multiWork = multiRun.next(max)
					if(multiWork.isEmpty)
						processedNextWork
					else
					{
						val expandedWork = (processedNextWork /: multiWork)(expand)
						val remaining = max - (expandedWork.size - processedNextWork.size)
						nextImpl(remaining, expandedWork)
					}
				}
				else
					processedNextWork
			}
			else
				processedNextWork
		}
		private def expand(accumulate: List[D], work: D): List[D] =
		{
			work match
			{
				case c: CompoundWork[D] =>
					val subWork = c.work
					addFinal(subWork.scheduler, new FinalWork(work, subWork.doFinally))
					accumulate
				case _ => work :: accumulate
			}
		}
		private def addFinal(schedule: Scheduler[D], work: FinalWork[D]) { multiRun += (schedule.run, Some(work)) }
	}
}
private object CompoundScheduler
{
	def apply[D](scheduler: Scheduler[D], strategy: ScheduleStrategy[D]) : Scheduler[D] =
		new CompoundScheduler(new MultiScheduler[D, Option[FinalWork[D]]]( (scheduler, None) ), strategy)
}
private final class FinalWork[D](val compound: D, val doFinally: Scheduler[D]) extends NotNull
/** This represents nested work.  The work provided by `scheduler` is processed first.  The work provided by `doFinally` is processed
* after `scheduler` completes regardless of the success of `scheduler`.*/
final class SubWork[D](val scheduler: Scheduler[D], val doFinally: Scheduler[D]) extends NotNull
/** Work that implements this interface provides nested work to be done before this work is processed.*/
trait CompoundWork[D] extends NotNull
{
	def work: SubWork[D]
}