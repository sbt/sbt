package coursier.util

import java.util.concurrent.{ExecutorService, Executors, ThreadFactory}

import scala.language.higherKinds

trait Schedulable[F[_]] extends Gather[F] {
  def schedule[A](pool: ExecutorService)(f: => A): F[A]
}

object Schedulable {

  def fixedThreadPool(size: Int): ExecutorService =
    Executors.newFixedThreadPool(
      size,
      // from scalaz.concurrent.Strategy.DefaultDaemonThreadFactory
      new ThreadFactory {
        val defaultThreadFactory = Executors.defaultThreadFactory()
        def newThread(r: Runnable) = {
          val t = defaultThreadFactory.newThread(r)
          t.setDaemon(true)
          t
        }
      }
    )

}
