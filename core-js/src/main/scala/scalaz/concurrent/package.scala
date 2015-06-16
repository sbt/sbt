package scalaz

import scala.concurrent.{ExecutionContext, Future}

/** Minimal Future-based Task */
package object concurrent {

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

    implicit val taskFunctor: Functor[Task] =
      new Functor[Task] {
        def map[A, B](fa: Task[A])(f: A => B): Task[B] =
          fa.map(f)
      }
  }

}
