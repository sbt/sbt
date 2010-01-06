/* sbt -- Simple Build Tool
 * Copyright 2009, 2010 Mark Harrah
 */
package xsbt

import java.io.File

trait TaskDefinition[T]
{
	val task: Task[T]
	val clean: Task[Unit]
}
trait TrackedTaskDefinition[T] extends TaskDefinition[T] with WithCache
{
	val tracked: Seq[Tracked]
	lazy val clean: Task[Unit] = onTracked(_.clean).bind( u => onTracked(_.clear) )
	import Task._
	private def onTracked(f: Tracked => Task[Unit]) = tracked.forkTasks(f).joinIgnore
}
trait WithCache
{
	def cacheDirectory: File
	def cacheFile(relative: String) = new File(cacheDirectory, relative)
}