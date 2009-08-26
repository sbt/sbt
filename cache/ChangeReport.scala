package xsbt

trait ChangeReport[T] extends NotNull
{
	def allInputs: Set[T]
	def unmodified: Set[T]
	def modified: Set[T] // all changes, including added
	def added: Set[T]
	def removed: Set[T]
	def +++(other: ChangeReport[T]): ChangeReport[T] = new CompoundChangeReport(this, other)
}
trait InvalidationReport[T] extends NotNull
{
	def valid: Set[T]
	def invalid: Set[T]
	def invalidProducts: Set[T]
}
private class CompoundChangeReport[T](a: ChangeReport[T], b: ChangeReport[T]) extends ChangeReport[T]
{
	lazy val allInputs = a.allInputs ++ b.allInputs
	lazy val unmodified = a.unmodified ++ b.unmodified
	lazy val modified = a.modified ++ b.modified
	lazy val added = a.added ++ b.added
	lazy val removed = a.removed ++ b.removed
}