package xsbt.boot

import java.io.{File, FileOutputStream}
import java.nio.channels.FileChannel
import java.util.concurrent.Callable

// gets a file lock by first getting a JVM-wide lock.
object Locks extends xsbti.GlobalLock
{
	import scala.collection.mutable.HashMap
	private[this] val locks = new HashMap[File, GlobalLock]
	def apply[T](file: File, action: Callable[T]) =
	{
		val canonFile = file.getCanonicalFile
		synchronized { locks.getOrElseUpdate(canonFile, new GlobalLock(canonFile)).withLock(action) }
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
					println("Waiting for lock on " + file + " to be available...");
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
