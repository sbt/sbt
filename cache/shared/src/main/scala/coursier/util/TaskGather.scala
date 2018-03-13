package coursier.util

import scala.concurrent.Future

trait TaskGather extends Gather[Task] {
  def point[A](a: A) = Task.point(a)
  def bind[A, B](elem: Task[A])(f: A => Task[B]) =
    elem.flatMap(f)

  def gather[A](elems: Seq[Task[A]]) =
    Task(implicit ec => Future.sequence(elems.map(_.value(ec))))
}
