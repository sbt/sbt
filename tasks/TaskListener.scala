package xsbt

trait TaskListener extends NotNull
{
	def added(t: Task[_]): Unit
	def runnable(t: Task[_]): Unit
	def running(t: Task[_]): Unit
	def calling(caller: Task[_], t: Task[_]): Unit
	def called(caller: Task[_], t: Task[_]): Unit
	def completed[T](t: Task[T], value: Option[T]): Unit
	def failed[T](t: Task[T], exception: Throwable): Unit
}
class BasicTaskListener extends TaskListener
{
	def added(t: Task[_]) {}
	def runnable(t: Task[_]) {}
	def running(t: Task[_]) {}
	def calling(caller: Task[_], t: Task[_]) {}
	def called(caller: Task[_], t: Task[_]) {}
	def completed[T](t: Task[T], value: Option[T]) {}
	def failed[T](t: Task[T], exception: Throwable) {}
}
class DebugTaskListener extends TaskListener
{
	def added(t: Task[_]) { debug("Added " + t) }
	def runnable(t: Task[_]) { debug("Runnable " + t)}
	def running(t: Task[_]) { debug("Running " + t) }
	def calling(caller: Task[_], t: Task[_]) { debug(caller + " calling " + t)}
	def called(caller: Task[_], t: Task[_]) { debug(caller + " called " + t)}
	def completed[T](t: Task[T], value: Option[T]) { debug("Completed " + t + " with " + value)}
	def failed[T](t: Task[T], exception: Throwable) { debug("Failed " + t + " with " + exception.toString); exception.printStackTrace }
	private def debug(msg: String) { println(msg) }
}