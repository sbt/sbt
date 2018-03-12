package coursier

import scala.concurrent.{ ExecutionContext, Future }
import scalaz.{ Nondeterminism, Reducer }

/**
 * Minimal Future-based Task.
 *
 * Likely to be flawed and/or sub-optimal, but does the job.
 */
trait Task[T] { self =>
  def map[U](f: T => U): Task[U] =
    new Task[U] {
      def runF(implicit ec: ExecutionContext) = self.runF.map(f)
    }
  def flatMap[U](f: T => Task[U]): Task[U] =
    new Task[U] {
      def runF(implicit ec: ExecutionContext) = self.runF.flatMap(f(_).runF)
    }

  def runF(implicit ec: ExecutionContext): Future[T]
}

object Task {
  def now[A](a: A): Task[A] =
    new Task[A] {
      def runF(implicit ec: ExecutionContext) = Future.successful(a)
    }
  def apply[A](f: ExecutionContext => Future[A]): Task[A] =
    new Task[A] {
      def runF(implicit ec: ExecutionContext) = f(ec)
    }
  def gatherUnordered[T](tasks: Seq[Task[T]], exceptionCancels: Boolean = false): Task[Seq[T]] =
    new Task[Seq[T]] {
      def runF(implicit ec: ExecutionContext) = Future.traverse(tasks)(_.runF)
    }

  implicit val taskMonad: Nondeterminism[Task] =
    new Nondeterminism[Task] {
      def point[A](a: => A): Task[A] = Task.now(a)
      def bind[A,B](fa: Task[A])(f: A => Task[B]): Task[B] = fa.flatMap(f)
      override def reduceUnordered[A, M](fs: Seq[Task[A]])(implicit R: Reducer[A, M]): Task[M] =
        Task { implicit ec =>
          val f = Future.sequence(fs.map(_.runF))
          f.map { l =>
            (R.zero /: l)(R.snoc)
          }
        }
      def chooseAny[A](head: Task[A], tail: Seq[Task[A]]): Task[(A, Seq[Task[A]])] =
        ???
    }
}
