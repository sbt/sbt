/* sbt -- Simple Build Tool
 * Copyright 2009, 2010  Mark Harrah
 */
package xsbt.boot

import java.io.{File, FileOutputStream}
import java.nio.channels.FileChannel
import java.util.concurrent.Callable
import scala.collection.immutable.List

object GetLocks
{
	/** Searches for Locks in parent class loaders before returning Locks from this class loader.
	* Normal class loading doesn't work because the launcher class loader hides xsbt classes.*/
	def find: xsbti.GlobalLock =
		Loaders(getClass.getClassLoader.getParent).flatMap(tryGet).headOption.getOrElse(Locks)
	private[this] def tryGet(loader: ClassLoader): List[xsbti.GlobalLock] =
		try { getLocks0(loader) :: Nil } catch { case e: ClassNotFoundException => Nil }
	private[this] def getLocks0(loader: ClassLoader) =
		Class.forName("xsbt.boot.Locks$", true, loader).getField("MODULE$").get(null).asInstanceOf[xsbti.GlobalLock]
}

// gets a file lock by first getting a JVM-wide lock.
object Locks extends xsbti.GlobalLock
{
	private[this] val locks = new Cache[File, GlobalLock](new GlobalLock(_))
	def apply[T](file: File, action: Callable[T]): T =
		synchronized
		{
			file.getParentFile.mkdirs()
			file.createNewFile()
			locks(file.getCanonicalFile).withLock(action)
		}

	private[this] class GlobalLock(file: File)
	{
		private[this] var fileLocked = false
		def withLock[T](run: Callable[T]): T =
			synchronized
			{
				if(fileLocked)
					run.call
				else
				{
					fileLocked = true
					try { withFileLock(run) }
					finally { fileLocked = false }
				}
			}
		private[this] def withFileLock[T](run: Callable[T]): T =
		{
			def withChannel(channel: FileChannel) =
			{
				val freeLock = channel.tryLock
				if(freeLock eq null)
				{
					System.out.println("Waiting for lock on " + file + " to be available...");
					val lock = channel.lock
					try { run.call }
					finally { lock.release() }
				}
				else
				{
					try { run.call }
					finally { freeLock.release() }
				}
			}
			Using(new FileOutputStream(file).getChannel)(withChannel)
		}
	}
}
