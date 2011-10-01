/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

import Types._
import scala.reflect.Manifest

// T must be invariant to work properly.
//  Because it is sealed and the only instances go through AttributeKey.apply,
//  a single AttributeKey instance cannot conform to AttributeKey[T] for different Ts
sealed trait AttributeKey[T] {
	def manifest: Manifest[T]
	def label: String
	def description: Option[String]
	def extend: Seq[AttributeKey[_]]
	def isLocal: Boolean
}
private[sbt] abstract class SharedAttributeKey[T] extends AttributeKey[T] {
	override final def toString = label
	override final def hashCode = label.hashCode
	override final def equals(o: Any) = (this eq o.asInstanceOf[AnyRef]) || (o match {
		case a: SharedAttributeKey[t] => a.label == this.label && a.manifest == this.manifest
		case _ => false
	})
	final def isLocal: Boolean = false
}
object AttributeKey
{
	def apply[T](name: String)(implicit mf: Manifest[T]): AttributeKey[T] = new SharedAttributeKey[T] {
		def manifest = mf
		def label = name
		def description = None
		def extend = Nil
	}
	def apply[T](name: String, description0: String)(implicit mf: Manifest[T]): AttributeKey[T] = new SharedAttributeKey[T] {
		def manifest = mf
		def label = name
		def description = Some(description0)
		def extend = Nil
	}
	def apply[T](name: String, description0: String, extend0: Seq[AttributeKey[_]])(implicit mf: Manifest[T]): AttributeKey[T] = new SharedAttributeKey[T] {
		def manifest = mf
		def label = name
		def description = Some(description0)
		def extend = extend0
	}
	private[sbt] def local[T](implicit mf: Manifest[T]): AttributeKey[T] = new AttributeKey[T] {
		def manifest = mf
		def label = LocalLabel
		def description = None
		def extend = Nil
		override def toString = label
		def isLocal: Boolean = true
	}
	private[sbt] final val LocalLabel = "$local"
}

trait AttributeMap
{
	def apply[T](k: AttributeKey[T]): T
	def get[T](k: AttributeKey[T]): Option[T]
	def remove[T](k: AttributeKey[T]): AttributeMap
	def contains[T](k: AttributeKey[T]): Boolean
	def put[T](k: AttributeKey[T], value: T): AttributeMap
	def keys: Iterable[AttributeKey[_]]
	def ++(o: Iterable[AttributeEntry[_]]): AttributeMap
	def ++(o: AttributeMap): AttributeMap
	def entries: Iterable[AttributeEntry[_]]
	def isEmpty: Boolean
}
object AttributeMap
{
	val empty: AttributeMap = new BasicAttributeMap(Map.empty)
	def apply(entries: Iterable[AttributeEntry[_]]): AttributeMap = empty ++ entries
	def apply(entries: AttributeEntry[_]*): AttributeMap = empty ++ entries
	implicit def toNatTrans(map: AttributeMap): AttributeKey ~> Id = new (AttributeKey ~> Id) {
		def apply[T](key: AttributeKey[T]): T = map(key)
	}
}
private class BasicAttributeMap(private val backing: Map[AttributeKey[_], Any]) extends AttributeMap
{
	def isEmpty: Boolean = backing.isEmpty
	def apply[T](k: AttributeKey[T]) = backing(k).asInstanceOf[T]
	def get[T](k: AttributeKey[T]) = backing.get(k).asInstanceOf[Option[T]]
	def remove[T](k: AttributeKey[T]): AttributeMap = new BasicAttributeMap( backing - k )
	def contains[T](k: AttributeKey[T]) = backing.contains(k)
	def put[T](k: AttributeKey[T], value: T): AttributeMap = new BasicAttributeMap( backing.updated(k, value) )
	def keys: Iterable[AttributeKey[_]] = backing.keys
	def ++(o: Iterable[AttributeEntry[_]]): AttributeMap =
	{
		val newBacking = (backing /: o) { case (b, AttributeEntry(key, value)) => b.updated(key, value) }
		new BasicAttributeMap(newBacking)
	}
	def ++(o: AttributeMap): AttributeMap =
		o match {
			case bam: BasicAttributeMap => new BasicAttributeMap(backing ++ bam.backing)
			case _ => o ++ this
		}
	def entries: Iterable[AttributeEntry[_]] =
		for( (k: AttributeKey[kt], v) <- backing) yield AttributeEntry(k, v.asInstanceOf[kt])
	override def toString = entries.mkString("(", ", ", ")")
}

// type inference required less generality
final case class AttributeEntry[T](key: AttributeKey[T], value: T)
{
	override def toString = key.label + ": " + value
}

final case class Attributed[D](data: D)(val metadata: AttributeMap)
{
	def get[T](key: AttributeKey[T]): Option[T] = metadata.get(key)
	def put[T](key: AttributeKey[T], value: T): Attributed[D] = Attributed(data)(metadata.put(key, value))
	def map[T](f: D => T): Attributed[T] = Attributed(f(data))(metadata)
}
object Attributed
{
	def blankSeq[T](in: Seq[T]): Seq[Attributed[T]] = in map blank
	def blank[T](data: T): Attributed[T] = Attributed(data)(AttributeMap.empty)
}