package xsbt.boot

import Pre._

// preserves iteration order
sealed class ListMap[K,V] private(backing: List[(K,V)]) extends Iterable[(K,V)] with NotNull
{
	import ListMap.remove
	def update(k: K, v: V) = this.+( (k,v) )
	def +(pair: (K,V)) = copy(pair :: remove(backing,pair._1))
	def -(k: K) = copy(remove(backing,k))
	def get(k: K): Option[V] = backing.find(_._1 == k).map(_._2)
	def keys: List[K] = backing.reverse.map(_._1)
	def apply(k: K): V = get(k).getOrElse(error("Key " + k + " not found"))
	def contains(k: K): Boolean = get(k).isDefined
	def elements: Iterator[(K,V)] = backing.reverse.elements
	override def isEmpty: Boolean = backing.isEmpty
	override def toList = backing.reverse
	override def toSeq = toList
	protected def copy(newBacking: List[(K,V)]): ListMap[K,V] = new ListMap(newBacking)
	def default(defaultF: K => V): ListMap[K,V] =
		new ListMap[K,V](backing) {
			override def apply(k: K) = super.get(k).getOrElse(defaultF(k))
			override def copy(newBacking: List[(K,V)]) = super.copy(newBacking).default(defaultF)
		}
	override def toString = backing.mkString("ListMap(",",",")")
}
object ListMap
{
	def apply[K,V](pairs: (K,V)*) = new ListMap[K,V](pairs.toList.removeDuplicates)
	def empty[K,V] = new ListMap[K,V](Nil)
	private def remove[K,V](backing: List[(K,V)], k: K) = backing.filter(_._1 != k)
}
