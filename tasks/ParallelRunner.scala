/* sbt -- Simple Build Tool
 * Copyright 2009  Mark Harrah
 */
package xsbt

/** This file provides the parallel execution engine of sbt.  It is a fairly general module, with pluggable Schedulers and Strategies.
*
* There are three main componenets to the engine: Distributors, Schedulers, and Strategies.
*
* A Scheduler provides work that is ready to execute.
*
* A Strategy is used by a Scheduler to select the work to process from the work that is ready.  It is notified as work
* becomes ready.  It is requested to select work to process from the work that is ready.
*
* A Distributor uses a Scheduler to obtain work up to the maximum work allowed to run at once.  It runs each
* unit of work in its own Thread.  It then returns the work and either the computed value or the error that occured.
*
* The Scheduler and Strategy are called from the main thread and therefore do not need to be thread-safe.
**/

import java.util.concurrent.LinkedBlockingQueue
import scala.collection.{immutable, mutable}
import immutable.TreeSet

/** Processes work. */
trait Compute[Work[_],Result[_]] { def apply[A](w: Work[A]): Result[A] }
/** Requests work from `scheduler` and processes it using `compute`.  This class limits the amount of work processing at any given time
* to `workers`.*/
final class Distributor[O,Work[_],Result[_]](val scheduler: Scheduler[O,Work,Result], compute: Compute[Work,Result], workers: Int) extends NotNull
{
	require(workers > 0)
	final def run() = (new Run).run()

	private final class Run extends NotNull
	{
		import java.util.concurrent.LinkedBlockingQueue
		private[this] val schedule = scheduler.run
		/** The number of threads currently running. */
		private[this] var running = 0
		/** Pending notifications of completed work. */
		private[this] val complete = new LinkedBlockingQueue[Done[_]]
		
		private[Distributor] def run(): O =
		{
			def runImpl(): O =
			{
				next()
				if(isIdle && !schedule.hasPending) // test if all work is complete
				{
					assume(schedule.isComplete, "Distributor idle and the scheduler indicated no work pending, but scheduler indicates it is not complete.")
					schedule.result
				}
				else
				{
					waitForCompletedWork() // wait for some work to complete 
					runImpl() // continue
				}
			}
			try { runImpl() }
			finally { shutdown() }
		}
		private def shutdown(): Unit = all.foreach(_.work.put(None))
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
				nextWork.foreach(work => process(work))
			}
		}
		// wait on the blocking queue `complete` until some work finishes and notify the scheduler
		private def waitForCompletedWork()
		{
			assume(running > 0)
			val done = complete.take()
			running -= 1
			notifyScheduler(done)
		}
		private def notifyScheduler[T](done: Done[T]): Unit = schedule.complete(done.work, done.result)
		private def process[T](work: Work[T])
		{
			assume(running + 1 <= workers)
			running += 1
			available.take().work.put(Some(work))
		}
		private[this] val all = List.tabulate(workers, i => new Worker)
		private[this] val available =
		{
			val q = new LinkedBlockingQueue[Worker]
			all.foreach(q.put)
			q
		}
		private final class Worker extends Thread with NotNull
		{
			lazy val work =
			{
				start()
				new LinkedBlockingQueue[Option[Work[_]]]
			}
			override def run()
			{
				def processData[T](data: Work[T])
				{
					val result = ErrorHandling.wideConvert(compute(data))
					complete.put( new Done(result, data) )
				}
				def runImpl()
				{
					work.take() match
					{
						case Some(data) =>
							processData(data)
							available.put(this)
							runImpl()
						case None => ()
					}
				}
				try { runImpl() }
				catch { case e: InterruptedException => () }
			}
		}
	}
	private final class Done[T](val result: Either[Throwable, Result[T]], val work: Work[T]) extends NotNull
}
/** Schedules work of type Work that produces results of type Result.  A Scheduler determines what work is ready to be processed.
* A Scheduler is itself immutable.  It creates a mutable object for each scheduler run.*/
trait Scheduler[O,Work[_],Result[_]] extends NotNull
{
	/** Starts a new run.  The returned object is a new Run, representing a single scheduler run.  All state for the run
	* is encapsulated in this object.*/
	def run: Run
	trait Run extends NotNull
	{
		/** Notifies this scheduler that work has completed with the given result.*/
		def complete[A](d: Work[A], result: Either[Throwable,Result[A]]): Unit
		/** Returns true if there is any more work to be done, although remaining work can be blocked
		* waiting for currently running work to complete.*/
		def hasPending: Boolean
		/**Returns true if this scheduler has no more work to be done, ever.*/
		def isComplete: Boolean
		/** Returns up to 'max' units of work.  `max` is always positive.  The returned sequence cannot be empty if there is
		* no work currently being processed.*/
		def next(max: Int): Seq[Work[_]]
		/** The final result after all work has completed. */
		def result: O
	}
}
/** A Strategy selects the work to process from work that is ready to be processed.*/
trait ScheduleStrategy[D] extends NotNull
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
final class SimpleStrategy[D] extends ScheduleStrategy[D]
{
	def run = new Run
	{
		private var ready = List[D]()
		def workReady(dep: D) { ready ::= dep }
		def hasReady = !ready.isEmpty
		def next(max: Int): List[D] =
		{
			val ret = ready.take(max)
			ready = ready.drop(max)
			ret
		}
	}
}