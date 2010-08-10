/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

import Types._

trait RMap[K[_], V[_]]
{
	def apply[T](k: K[T]): V[T]
	def get[T](k: K[T]): Option[V[T]]
	def contains[T](k: K[T]): Boolean
}
trait PMap[K[_], V[_]] extends (K ~> V) with RMap[K,V]
{
	def update[T](k: K[T], v: V[T]): Unit
	def remove[T](k: K[T]): Option[V[T]]
	def getOrUpdate[T](k: K[T], make: => V[T]): V[T]
}
object PMap
{
	implicit def toFunction[K[_], V[_]](map: PMap[K,V]): K[_] => V[_] = k => map(k)
}

abstract class AbstractPMap[K[_], V[_]] extends PMap[K,V]
{
	def apply[T](k: K[T]): V[T] = get(k).get
	def contains[T](k: K[T]): Boolean = get(k).isDefined
}

import collection.mutable.Map

/**
* Only suitable for K that is invariant in its type parameter.
* Option and List keys are not suitable, for example,
*  because None &lt;:&lt; Option[String] and None &lt;: Option[Int].
*/
class DelegatingPMap[K[_], V[_]](backing: Map[K[_], V[_]]) extends AbstractPMap[K,V]
{
	def get[T](k: K[T]): Option[V[T]] = cast[T]( backing.get(k) )
	def update[T](k: K[T], v: V[T]) { backing(k) = v }
	def remove[T](k: K[T]) = cast( backing.remove(k) )
	def getOrUpdate[T](k: K[T], make: => V[T]) = cast[T]( backing.getOrElseUpdate(k, make) )

	private[this] def cast[T](v: V[_]): V[T] = v.asInstanceOf[V[T]]
	private[this] def cast[T](o: Option[V[_]]): Option[V[T]] = o map cast[T]

	override def toString = backing.toString
}