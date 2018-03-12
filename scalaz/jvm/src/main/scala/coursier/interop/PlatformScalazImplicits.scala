package coursier.interop

import java.util.concurrent.ExecutorService

import coursier.util.Schedulable
import _root_.scalaz.concurrent.{Task => ScalazTask}

abstract class PlatformScalazImplicits {

  implicit val scalazTaskSchedulable: Schedulable[ScalazTask] =
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

}
