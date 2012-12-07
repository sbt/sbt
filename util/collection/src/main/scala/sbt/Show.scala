package sbt

trait Show[T] {
	def apply(t: T): String
}
object Show
{
	def apply[T](f: T => String): Show[T] = new Show[T] { def apply(t: T): String = f(t) }
}