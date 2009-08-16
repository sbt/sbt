package xsbt

import scala.collection.{mutable,immutable}

 // immutable.HashSet is not suitable for multi-threaded access, so this
// implementation uses an underlying immutable.TreeHashMap, which is suitable
object TreeHashSet
{
	def apply[T](contents: T*) = new TreeHashSet(immutable.TreeHashMap( andUnit(contents) : _*))
	def andUnit[T](contents: Iterable[T]) = contents.map(c => (c,()) ).toSeq
}
final class TreeHashSet[T](backing: immutable.TreeHashMap[T,Unit]) extends immutable.Set[T]
{
	import TreeHashSet.andUnit
	override def contains(t: T) = backing.contains(t)
	override def ++(s: Iterable[T]) = new TreeHashSet(backing ++ andUnit(s))
	override def +(s: T) = ++( Seq(s) )
	override def -(s: T) = new TreeHashSet(backing - s)
	override def elements = backing.keys
	override def empty[A] = TreeHashSet[A]()
	override def size = backing.size
}