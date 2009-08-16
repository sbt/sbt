package xsbt

import scala.collection.{immutable,mutable}
import Task.ITask

final case class WorkFailure[D](work: D, exception: Throwable) extends NotNull
{
	def map[C](f: D => C) = WorkFailure(f(work), exception)
}
private final class TaskScheduler[O](root: Task[O], strategy: ScheduleStrategy[Work[_,_]], newListener: => TaskListener)
	extends Scheduler[ Either[ List[WorkFailure[Task[_]]], O ], Work.Job, Result]
{
	def run = new Run
	{
		val listener = newListener
		def result =
		{
			assume(reverseDeps.isEmpty)
			assume(forwardDeps.isEmpty)
			assume(calls.isEmpty)
			assume(!strategyRun.hasReady)
			if(failureReports.isEmpty)
				Right(completed(root))
			else
				Left(failureReports.toList)
		}
		def next(max: Int) =
		{
			val running = strategyRun.next(max)
			running.foreach(r => listener.running(r.source))
			running
		}
		def isComplete = reverseDeps.isEmpty
		def hasPending = strategyRun.hasReady || !forwardDeps.isEmpty
		def complete[A](work: Work.Job[A], result: Either[Throwable,Result[A]]): Unit =
		{
			val task = work.source
			result match
			{
				case Left(err) =>
					failureReports += WorkFailure(task, err)
					listener.failed(task, err)
					retire(task, None)
					assert(failed.contains(task))
				case Right(value) =>
					success(task, value)
					assert(completed.contains(task) || (calls.isCalling(task) && !reverseDeps.isEmpty) || failed.contains(task))
			}
			assert(calls.isCalling(task) || !reverseDeps.contains(task))
			assert(!forwardDeps.contains(task))
		}

		private def newDepMap = new mutable.HashMap[Task[_], mutable.Set[Task[_]]]
		private val reverseDeps = newDepMap
		private val forwardDeps = newDepMap
		private val calls = new CalledByMap
		private val completed = new ResultMap
		private val strategyRun = strategy.run
		private val failed = new mutable.HashSet[Task[_]]
		private val failureReports = new mutable.ArrayBuffer[WorkFailure[Task[_]]]
		
		{
			val initialized = addGraph(root, root) // TODO: replace second root with something better? (it is ignored here anyway)
			assert(initialized)
		}
		
		private def addReady[O](m: Task[O])
		{
			def add[I](m: ITask[I,O])
			{
				val input = Task.extract(m, completed)
				strategyRun.workReady(new Work(m, input))
				listener.runnable(m)
			}
			
			assert(!forwardDeps.contains(m), m)
			assert(reverseDeps.contains(m), m)
			assert(!completed.contains(m), m)
			assert(!calls.isCalling(m), m)
			assert(m.dependencies.forall(completed.contains), "Could not find result for dependency of ready task " + m)
			add(m: ITask[_,O])
		}
		// context called node
		private def addGraph(node: Task[_], context: Task[_]): Boolean =
		{
			if(failed(node)) // node already failed
				false
			else if(calls.isCalling(node)) // node is waiting for a called task to complete, so we need to check for circular dependencies
			{
				if(calls.isCallerOf(node, context)) // if node called context, this is a circular dependency and is invalid
				{
					failureReports += WorkFailure(node, CircularDependency(node, context))
					false
				}
				else
					true
			}
			else if(reverseDeps.contains(node) || completed.contains(node)) // node is either already added and is waiting for dependencies to complete or it has completed
				true
			else // node has never been added
				newAdd(node, context)
		}
		private def newAdd(node: Task[_], context: Task[_]): Boolean =
		{
			val deps = node.dependencies.filter(dep => !completed.contains(dep))
			def finishAdding() =
			{
				listener.added(node)
				true
			}
			if(deps.isEmpty) // node is ready to be run
			{
				reverseDeps(node) = new mutable.HashSet[Task[_]]
				addReady(node)
				finishAdding()
			}
			else if(deps.forall(dep => addGraph(dep,context))) // node requires dependencies to be added successfully and will then wait for them to complete before running
			{
				for(dep <- node.dependencies if !(completed.contains(dep) || reverseDeps.contains(dep) || calls.isCalling(dep)))
					error("Invalid dependency state: (completed=" + completed.contains(dep) + ", reverse=" + reverseDeps.contains(dep) + ", calling=" + calls.isCalling(dep) + ") for " + dep)
				reverseDeps(node) = new mutable.HashSet[Task[_]]
				deps.foreach(dep => reverseDeps(dep) += node) // mark this node as depending on its dependencies
				forwardDeps(node) = mutable.HashSet(deps.toSeq : _*)
				finishAdding()
			}
			else // a dependency could not be added, so this node will fail as well.
			{
				failed += node
				false
			}
		}
		private def retire[O](m: Task[O], value: Option[O])
		{
			value match
			{
				case Some(v) => completed(m) = v // map the task to its value
				case None => failed += m // mark the task as failed.  complete has already recorded the error message for the original cause
			}
			updateCurrentGraph(m, value.isDefined) // update forward and reverse dependency maps and propagate the change to depending tasks
			listener.completed(m, value)
			calls.remove(m) match  // unwind the call stack
			{
				case Some(c) =>
					listener.called(c, m)
					retire(c, value)
				case None => ()
			}
		}
		private def updateCurrentGraph[O](m: Task[O], success: Boolean)
		{
			if(!success)
			{
				// clear m from the forward dependency map
				//  for each dependency d of m, remove m from the set of tasks that depend on d
				for(depSet <- forwardDeps.removeKey(m); dep <- depSet; reverseSet <- reverseDeps.get(dep))
					reverseSet -= m
			}
			// m is complete, so remove its entry from reverseDeps and update all tasks that depend on m
			for(mReverseDeps <- reverseDeps.removeKey(m); dependsOnM <- mReverseDeps)
			{
				if(success)
				{
					val on = forwardDeps(dependsOnM)
					on -= m // m has completed, so remove it from the set of tasks that must complete before 'on' can run
					if(on.isEmpty) // m was the last dependency of on, so make it runnable
					{
						forwardDeps.removeKey(dependsOnM)
						addReady(dependsOnM)
					}
				}
				else // cancel dependsOnM because dependency (m) failed
					retire(dependsOnM, None)
			}
		}
		
		private def success[O](task: Task[O], value: Result[O]): Unit =
			value match
			{
				case NewTask(t) =>
					if(t == task)
					{
						failureReports += WorkFailure(t, CircularDependency(t, task))
						retire(task, None)
					}
					else if(addGraph(t, task))
					{
						calls(t) = task
						listener.calling(task, t)
					}
					else
						retire(task, None)
				case Value(v) => retire(task, Some(v))
			}
	}
}
final case class CircularDependency(node: Task[_], context: Task[_])
	extends RuntimeException("Task " + context + " provided task " + node + " already in calling stack")

private final class CalledByMap extends NotNull
{
	private[this] val calling = new mutable.HashSet[Task[_]]
	private[this] val callMap = new mutable.HashMap[Task[_], Task[_]]
	def update[O](called: Task[O], by: Task[O])
	{
		calling += by
		callMap(called) = by
	}
	final def isCallerOf(check: Task[_], frame: Task[_]): Boolean =
	{
		if(check eq frame) true
		else
			callMap.get(frame) match
			{
				case Some(nextFrame) => isCallerOf(check, nextFrame)
				case None => false
			}
	}
	def isEmpty = calling.isEmpty  && callMap.isEmpty
	def isCalled(task: Task[_]): Boolean = callMap.contains(task)
	def isCalling(caller: Task[_]): Boolean = calling(caller)
	def remove[O](called: Task[O]): Option[Task[O]] =
		for(caller <- callMap.removeKey(called)) yield
		{
			calling -= caller
			caller.asInstanceOf[Task[O]]
		}
}
private final class ResultMap(private val map: mutable.HashMap[Task[_], Any]) extends Results
{
	def this() = this(new mutable.HashMap)
	def update[O](task: Task[O], value: O) { map(task) = value }
	def apply[O](task: Task[O]): O = map(task).asInstanceOf[O]
	def contains(task: Task[_]) = map.contains(task)
}

private final class Work[I,O](val source: ITask[I,O], input: I) extends Identity with NotNull
{
	final def apply = Task.compute(source, input)
}
private object Work
{
	type Job[A] = Work[_,A]
}