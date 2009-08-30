package xsbt

import java.io.File

trait TaskDefinition[T]
{
	val task: Task[T]
	val clean: Task[Unit]
	val clear: Task[Unit]
}
trait TrackedTaskDefinition[T] extends TaskDefinition[T]
{
	def cacheDirectory: File
	def cacheFile(relative: String) = new File(cacheDirectory, relative)
	val tracked: Seq[Tracked]
	val clear: Task[Unit] = foreachCache(_.clear)
	val clean: Task[Unit] = foreachCache(_.clean)
	private def foreachCache(f: Tracked => Task[Unit]): Task[Unit] = tracked.map(f).join.map(i => ())
}