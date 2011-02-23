package xsbti

object g0
{
	def apply[T](s: => T) = new F0[T] { def apply = s }
}