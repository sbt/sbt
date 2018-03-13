package coursier.util

import java.util.concurrent.ExecutorService

import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutorService, Future}
import scala.concurrent.duration.Duration

abstract class PlatformTask { self =>

  def schedule[A](pool: ExecutorService)(f: => A): Task[A] = {

    val ec0 = pool match {
      case eces: ExecutionContextExecutorService => eces
      case _ => ExecutionContext.fromExecutorService(pool) // FIXME Is this instantiation costly? Cache it?
    }

    Task(_ => Future(f)(ec0))
  }

  implicit val schedulable: Schedulable[Task] =
    new TaskGather with Schedulable[Task] {
      def schedule[A](pool: ExecutorService)(f: => A) = self.schedule(pool)(f)
    }

  def gather: Gather[Task] =
    schedulable

  implicit class PlatformTaskOps[T](private val task: Task[T]) {
    def unsafeRun()(implicit ec: ExecutionContext): T =
      Await.result(task.future(), Duration.Inf)
  }

}
