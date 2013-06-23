package sbt

	import java.util.concurrent.ConcurrentHashMap
	import TaskName._

private[sbt] final class TaskTimings extends ExecuteProgress[Task]
{
	private[this] val calledBy = new ConcurrentHashMap[Task[_], Task[_]]
	private[this] val anonOwners = new ConcurrentHashMap[Task[_], Task[_]]
	private[this] val timings = new ConcurrentHashMap[Task[_], Long]
	private[this] var start = 0L

	type S = Unit

	def initial = { start = System.nanoTime }
	def registered(state: Unit, task: Task[_], allDeps: Iterable[Task[_]], pendingDeps: Iterable[Task[_]]) = {
		pendingDeps foreach { t => if(transformNode(t).isEmpty) anonOwners.put(t,task) }
	}
	def ready(state: Unit, task: Task[_]) = ()
	def workStarting(task: Task[_]) = timings.put(task, System.nanoTime)
	def workFinished[T](task: Task[T], result: Either[Task[T], Result[T]]) = {
		timings.put(task, System.nanoTime - timings.get(task))
		result.left.foreach { t => calledBy.put(t, task) }
	}
	def completed[T](state: Unit, task: Task[T], result: Result[T]) = ()
	def allCompleted(state: Unit, results: RMap[Task,Result]) =
	{
		val total = System.nanoTime - start
		println("Total time: " + (total*1e-6) + " ms")
			import collection.JavaConversions._
		def sumTimes(in: Seq[(Task[_], Long)]) = in.map(_._2).sum
		val timingsByName = timings.toSeq.groupBy { case (t, time) => mappedName(t) } mapValues(sumTimes)
		for( (taskName, time) <- timingsByName.toSeq.sortBy(_._2).reverse)
			println("  " + taskName + ": " + (time*1e-6) + " ms")
	}
	private[this] def inferredName(t: Task[_]): Option[String] = nameDelegate(t) map mappedName
	private[this] def nameDelegate(t: Task[_]): Option[Task[_]] = Option(anonOwners.get(t)) orElse Option(calledBy.get(t))
	private[this] def mappedName(t: Task[_]): String = definedName(t) orElse inferredName(t) getOrElse anonymousName(t)
}
