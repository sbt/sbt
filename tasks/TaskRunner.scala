package xsbt

object TaskRunner
{
	def apply[T](node: Task[T]): T = apply(node, Runtime.getRuntime.availableProcessors)
	/** Executes work for nodes in a directed acyclic graph with root node `node`.
	* The maximum number of tasks to execute simultaneously is `maximumTasks`. */
	def apply[T](node: Task[T], maximumTasks: Int): T =
	{
		require(maximumTasks > 0)
		val compute = new Compute[Work.Job, Result] { def apply[A](w: Work.Job[A]) = w.apply }
		val strategy = new SimpleStrategy[Work[_,_]]
		val scheduler = new TaskScheduler(node, strategy, new BasicTaskListener)
		val distributor = new Distributor[ Either[ List[WorkFailure[Task[_]]], T ] , Work.Job, Result](scheduler, compute, maximumTasks)
		distributor.run().fold(failures => throw new TasksFailed(failures), identity[T])
	}
}
final case class TasksFailed(failures: List[WorkFailure[Task[_]]]) extends RuntimeException(failures.length + " tasks failed")
{
	override def toString = failures.mkString(getMessage + "\n", "\n\t", "\n")
}