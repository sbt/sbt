package coursier.util

import java.util.concurrent.{ExecutorService, Executors, ThreadFactory}

import scala.language.higherKinds
import scalaz.concurrent.{Task => ScalazTask}

trait Schedulable[F[_]] extends Gather[F] {
  def schedule[A](pool: ExecutorService)(f: => A): F[A]
}

object Schedulable {

  implicit val scalazTask: Schedulable[ScalazTask] =
    new Schedulable[ScalazTask] {
      def point[A](a: A) =
        ScalazTask.point(a)
      def schedule[A](pool: ExecutorService)(f: => A) =
        ScalazTask(f)(pool)

      def gather[A](elems: Seq[ScalazTask[A]]) =
        ScalazTask.taskInstance.gather(elems)

      def bind[A, B](elem: ScalazTask[A])(f: A => ScalazTask[B]) =
        ScalazTask.taskInstance.bind(elem)(f)
    }

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
