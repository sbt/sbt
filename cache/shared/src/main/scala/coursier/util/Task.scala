package coursier.util

import scala.concurrent.{ExecutionContext, Future}

final case class Task[T](value: ExecutionContext => Future[T]) extends AnyVal {

  def map[U](f: T => U): Task[U] =
    Task(implicit ec => value(ec).map(f))
  def flatMap[U](f: T => Task[U]): Task[U] =
    Task(implicit ec => value(ec).flatMap(t => f(t).value(ec)))

  def handle[U >: T](f: PartialFunction[Throwable, U]): Task[U] =
    Task(ec => value(ec).recover(f)(ec))

  def future()(implicit ec: ExecutionContext): Future[T] =
    value(ec)
}

object Task extends PlatformTask {

  def point[A](a: A): Task[A] = {
    val future = Future.successful(a)
    Task(_ => future)
  }

  def delay[A](a: => A): Task[A] =
    Task(ec => Future(a)(ec))

}

