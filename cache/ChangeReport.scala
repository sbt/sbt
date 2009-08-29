package xsbt

object ChangeReport
{
	def modified[T](files: Set[T]) =
		new EmptyChangeReport[T]
		{
			override def allInputs = files
			override def modified = files
			override def markAllModified = this
		}
	def unmodified[T](files: Set[T]) =
		new EmptyChangeReport[T]
		{
			override def allInputs = files
			override def unmodified = files
		}
}
trait ChangeReport[T] extends NotNull
{
	def allInputs: Set[T]
	def unmodified: Set[T]
	def modified: Set[T] // all changes, including added
	def added: Set[T]
	def removed: Set[T]
	def +++(other: ChangeReport[T]): ChangeReport[T] = new CompoundChangeReport(this, other)
	def markAllModified: ChangeReport[T] =
		new ChangeReport[T]
		{
			def allInputs = ChangeReport.this.allInputs
			def unmodified = Set.empty[T]
			def modified = ChangeReport.this.allInputs
			def added = ChangeReport.this.added
			def removed = ChangeReport.this.removed
			override def markAllModified = this
		}
}
class EmptyChangeReport[T] extends ChangeReport[T]
{
	def allInputs = Set.empty[T]
	def unmodified = Set.empty[T]
	def modified = Set.empty[T]
	def added = Set.empty[T]
	def removed = Set.empty[T]
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