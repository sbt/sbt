package sbt.util

trait Show[A] {
  def show(a: A): String
}
object Show {
  def apply[A](f: A => String): Show[A] = new Show[A] { def show(a: A): String = f(a) }

  def fromToString[A]: Show[A] = new Show[A] {
    def show(a: A): String = a.toString
  }
}
