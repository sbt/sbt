package sbt

// Todo share this this io.syntax
private[sbt] trait IOSyntax0 extends IOSyntax1 {
  implicit def alternative[A, B](f: A => Option[B]): Alternative[A, B] =
    new Alternative[A, B] {
      def |(g: A => Option[B]) =
        (a: A) => f(a) orElse g(a)
    }
}
private[sbt] trait Alternative[A, B] {
  def |(g: A => Option[B]): A => Option[B]
}

private[sbt] trait IOSyntax1 {
  implicit def singleFileFinder(file: File): sbt.io.PathFinder = sbt.io.PathFinder(file)
}
