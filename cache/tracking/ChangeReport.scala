/* sbt -- Simple Build Tool
 * Copyright 2009, 2010 Mark Harrah
 */
package sbt

object ChangeReport
{
	def modified[T](files: Set[T]) =
		new EmptyChangeReport[T]
		{
			override def checked = files
			override def modified = files
			override def markAllModified = this
		}
	def unmodified[T](files: Set[T]) =
		new EmptyChangeReport[T]
		{
			override def checked = files
			override def unmodified = files
		}
}
/** The result of comparing some current set of objects against a previous set of objects.*/
trait ChangeReport[T] extends NotNull
{
	/** The set of all of the objects in the current set.*/
	def checked: Set[T]
	/** All of the objects that are in the same state in the current and reference sets.*/
	def unmodified: Set[T]
	/** All checked objects that are not in the same state as the reference.  This includes objects that are in both
	* sets but have changed and files that are only in one set.*/
	def modified: Set[T] // all changes, including added
	/** All objects that are only in the current set.*/
	def added: Set[T]
	/** All objects only in the previous set*/
	def removed: Set[T]
	def +++(other: ChangeReport[T]): ChangeReport[T] = new CompoundChangeReport(this, other)
	/** Generate a new report with this report's unmodified set included in the new report's modified set.  The new report's
	* unmodified set is empty.  The new report's added, removed, and checked sets are the same as in this report. */
	def markAllModified: ChangeReport[T] =
		new ChangeReport[T]
		{
			def checked = ChangeReport.this.checked
			def unmodified = Set.empty[T]
			def modified = ChangeReport.this.checked
			def added = ChangeReport.this.added
			def removed = ChangeReport.this.removed
			override def markAllModified = this
		}
	override def toString =
	{
		val labels = List("Checked", "Modified", "Unmodified", "Added", "Removed")
		val sets = List(checked, modified, unmodified, added, removed)
		val keyValues = labels.zip(sets).map{ case (label, set) => label + ": " + set.mkString(", ") }
		keyValues.mkString("Change report:\n\t", "\n\t", "")
	}
}
class EmptyChangeReport[T] extends ChangeReport[T]
{
	def checked = Set.empty[T]
	def unmodified = Set.empty[T]
	def modified = Set.empty[T]
	def added = Set.empty[T]
	def removed = Set.empty[T]
	override def toString = "No changes"
}
private class CompoundChangeReport[T](a: ChangeReport[T], b: ChangeReport[T]) extends ChangeReport[T]
{
	lazy val checked = a.checked ++ b.checked
	lazy val unmodified = a.unmodified ++ b.unmodified
	lazy val modified = a.modified ++ b.modified
	lazy val added = a.added ++ b.added
	lazy val removed = a.removed ++ b.removed
}