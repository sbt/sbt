package xsbti

object f0
{
	def apply[T](s: => T) = new F0[T] { def apply = s }
}