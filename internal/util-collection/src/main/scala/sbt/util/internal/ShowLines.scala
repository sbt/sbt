package sbt.util.internal

trait ShowLines[A] {
  def showLines(a: A): Seq[String]
}
object ShowLines {
  def apply[A](f: A => Seq[String]): ShowLines[A] =
    new ShowLines[A] {
      def showLines(a: A): Seq[String] = f(a)
    }

  implicit class ShowLinesOp[A: ShowLines](a: A) {
    def lines: Seq[String] = implicitly[ShowLines[A]].showLines(a)
  }
}
