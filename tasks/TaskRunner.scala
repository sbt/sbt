package xsbt

object TaskRunner
{
	def apply[T](node: Task[T]): Either[ List[WorkFailure[Task[_]]] , T ] = apply(node, Runtime.getRuntime.availableProcessors)
	/** Executes work for nodes in a directed acyclic graph with root node `node`.
	* The maximum number of tasks to execute simultaneously is `maximumTasks`. */
	def apply[T](node: Task[T], maximumTasks: Int): Either[ List[WorkFailure[Task[_]]] , T ] =
	{
		require(maximumTasks > 0)
		val compute = new Compute[Work.Job, Result] { def apply[A](w: Work.Job[A]) = w.apply }
		val strategy = new SimpleStrategy[Work[_,_]]
		val scheduler = new TaskScheduler(node, strategy, new BasicTaskListener)
		val distributor = new Distributor[ Either[ List[WorkFailure[Task[_]]], T ] , Work.Job, Result](scheduler, compute, maximumTasks)
		distributor.run()
	}
}