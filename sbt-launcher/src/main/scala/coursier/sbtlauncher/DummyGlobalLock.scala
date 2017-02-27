package coursier.sbtlauncher

import java.io.File
import java.util.concurrent.{Callable, ConcurrentHashMap}

case object DummyGlobalLock extends xsbti.GlobalLock {

  private val locks = new ConcurrentHashMap[File, AnyRef]

  def apply[T](lockFile: File, run: Callable[T]): T =
    Option(lockFile) match {
      case None => run.call()
      case Some(lockFile0) =>
        val lock0 = new AnyRef
        val lock = Option(locks.putIfAbsent(lockFile0, lock0)).getOrElse(lock0)

        lock.synchronized {
          run.call()
        }
    }
}
