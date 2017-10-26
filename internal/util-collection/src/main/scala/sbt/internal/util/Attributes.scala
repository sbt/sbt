/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.internal.util

import Types._
import scala.reflect.Manifest
import sbt.util.OptJsonWriter

// T must be invariant to work properly.
//  Because it is sealed and the only instances go through AttributeKey.apply,
//  a single AttributeKey instance cannot conform to AttributeKey[T] for different Ts

/**
 * A key in an [[AttributeMap]] that constrains its associated value to be of type `T`.
 * The key is uniquely defined by its [[label]] and type `T`, represented at runtime by [[manifest]].
 */
sealed trait AttributeKey[T] {

  /** The runtime evidence for `T` */
  def manifest: Manifest[T]

  /** The label is the identifier for the key and is camelCase by convention. */
  def label: String

  /** An optional, brief description of the key. */
  def description: Option[String]

  /**
   * In environments that support delegation, looking up this key when it has no associated value will delegate to the values associated with these keys.
   * The delegation proceeds in order the keys are returned here.
   */
  def extend: Seq[AttributeKey[_]]

  /**
   * Specifies whether this key is a local, anonymous key (`true`) or not (`false`).
   * This is typically only used for programmatic, intermediate keys that should not be referenced outside of a specific scope.
   */
  def isLocal: Boolean

  /** Identifies the relative importance of a key among other keys.*/
  def rank: Int

  def optJsonWriter: OptJsonWriter[T]

}

private[sbt] abstract class SharedAttributeKey[T] extends AttributeKey[T] {
  override final def toString = label
  override final def hashCode = label.hashCode
  override final def equals(o: Any) =
    (this eq o.asInstanceOf[AnyRef]) || (o match {
      case a: SharedAttributeKey[t] => a.label == this.label && a.manifest == this.manifest
      case _                        => false
    })
  final def isLocal: Boolean = false
}

object AttributeKey {
  def apply[T: Manifest: OptJsonWriter](name: String): AttributeKey[T] =
    make(name, None, Nil, Int.MaxValue)

  def apply[T: Manifest: OptJsonWriter](name: String, rank: Int): AttributeKey[T] =
    make(name, None, Nil, rank)

  def apply[T: Manifest: OptJsonWriter](name: String, description: String): AttributeKey[T] =
    apply(name, description, Nil)

  def apply[T: Manifest: OptJsonWriter](name: String,
                                        description: String,
                                        rank: Int): AttributeKey[T] =
    apply(name, description, Nil, rank)

  def apply[T: Manifest: OptJsonWriter](name: String,
                                        description: String,
                                        extend: Seq[AttributeKey[_]]): AttributeKey[T] =
    apply(name, description, extend, Int.MaxValue)

  def apply[T: Manifest: OptJsonWriter](name: String,
                                        description: String,
                                        extend: Seq[AttributeKey[_]],
                                        rank: Int): AttributeKey[T] =
    make(name, Some(description), extend, rank)

  private[sbt] def copyWithRank[T](a: AttributeKey[T], rank: Int): AttributeKey[T] =
    make(a.label, a.description, a.extend, rank)(a.manifest, a.optJsonWriter)

  private[this] def make[T](
      name: String,
      description0: Option[String],
      extend0: Seq[AttributeKey[_]],
      rank0: Int
  )(implicit mf: Manifest[T], ojw: OptJsonWriter[T]): AttributeKey[T] =
    new SharedAttributeKey[T] {
      require(name.headOption match {
        case Some(c) => c.isLower
        case None    => false
      }, s"A named attribute key must start with a lowercase letter: $name")

      def manifest = mf
      val label = Util.hyphenToCamel(name)
      def description = description0
      def extend = extend0
      def rank = rank0
      def optJsonWriter = ojw
    }

  private[sbt] def local[T](implicit mf: Manifest[T], ojw: OptJsonWriter[T]): AttributeKey[T] =
    new AttributeKey[T] {
      def manifest = mf
      def label = LocalLabel
      def description = None
      def extend = Nil
      override def toString = label
      def isLocal: Boolean = true
      def rank = Int.MaxValue
      val optJsonWriter = ojw
    }

  private[sbt] final val LocalLabel = "$" + "local"

}

/**
 * An immutable map where a key is the tuple `(String,T)` for a fixed type `T` and can only be associated with values of type `T`.
 * It is therefore possible for this map to contain mappings for keys with the same label but different types.
 * Excluding this possibility is the responsibility of the client if desired.
 */
trait AttributeMap {

  /**
   * Gets the value of type `T` associated with the key `k`.
   * If a key with the same label but different type is defined, this method will fail.
   */
  def apply[T](k: AttributeKey[T]): T

  /**
   * Gets the value of type `T` associated with the key `k` or `None` if no value is associated.
   * If a key with the same label but a different type is defined, this method will return `None`.
   */
  def get[T](k: AttributeKey[T]): Option[T]

  /**
   * Returns this map without the mapping for `k`.
   * This method will not remove a mapping for a key with the same label but a different type.
   */
  def remove[T](k: AttributeKey[T]): AttributeMap

  /**
   * Returns true if this map contains a mapping for `k`.
   * If a key with the same label but a different type is defined in this map, this method will return `false`.
   */
  def contains[T](k: AttributeKey[T]): Boolean

  /**
   * Adds the mapping `k -> value` to this map, replacing any existing mapping for `k`.
   * Any mappings for keys with the same label but different types are unaffected.
   */
  def put[T](k: AttributeKey[T], value: T): AttributeMap

  /** All keys with defined mappings.  There may be multiple keys with the same `label`, but different types. */
  def keys: Iterable[AttributeKey[_]]

  /** Adds the mappings in `o` to this map, with mappings in `o` taking precedence over existing mappings.*/
  def ++(o: Iterable[AttributeEntry[_]]): AttributeMap

  /** Combines the mappings in `o` with the mappings in this map, with mappings in `o` taking precedence over existing mappings.*/
  def ++(o: AttributeMap): AttributeMap

  /** All mappings in this map.  The [[AttributeEntry]] type preserves the typesafety of mappings, although the specific types are unknown.*/
  def entries: Iterable[AttributeEntry[_]]

  /** `true` if there are no mappings in this map, `false` if there are. */
  def isEmpty: Boolean

  /**
   * Adds the mapping `k -> opt.get` if opt is Some.
   * Otherwise, it returns this map without the mapping for `k`.
   */
  private[sbt] def setCond[T](k: AttributeKey[T], opt: Option[T]): AttributeMap
}

object AttributeMap {

  /** An [[AttributeMap]] without any mappings. */
  val empty: AttributeMap = new BasicAttributeMap(Map.empty)

  /** Constructs an [[AttributeMap]] containing the given `entries`. */
  def apply(entries: Iterable[AttributeEntry[_]]): AttributeMap = empty ++ entries

  /** Constructs an [[AttributeMap]] containing the given `entries`.*/
  def apply(entries: AttributeEntry[_]*): AttributeMap = empty ++ entries

  /** Presents an `AttributeMap` as a natural transformation. */
  implicit def toNatTrans(map: AttributeMap): AttributeKey ~> Id = λ[AttributeKey ~> Id](map(_))
}

private class BasicAttributeMap(private val backing: Map[AttributeKey[_], Any])
    extends AttributeMap {

  def isEmpty: Boolean = backing.isEmpty
  def apply[T](k: AttributeKey[T]) = backing(k).asInstanceOf[T]
  def get[T](k: AttributeKey[T]) = backing.get(k).asInstanceOf[Option[T]]
  def remove[T](k: AttributeKey[T]): AttributeMap = new BasicAttributeMap(backing - k)
  def contains[T](k: AttributeKey[T]) = backing.contains(k)

  def put[T](k: AttributeKey[T], value: T): AttributeMap =
    new BasicAttributeMap(backing.updated(k, value))

  def keys: Iterable[AttributeKey[_]] = backing.keys

  def ++(o: Iterable[AttributeEntry[_]]): AttributeMap = {
    val newBacking = (backing /: o) {
      case (b, AttributeEntry(key, value)) => b.updated(key, value)
    }
    new BasicAttributeMap(newBacking)
  }

  def ++(o: AttributeMap): AttributeMap =
    o match {
      case bam: BasicAttributeMap => new BasicAttributeMap(backing ++ bam.backing)
      case _                      => o ++ this
    }

  def entries: Iterable[AttributeEntry[_]] =
    backing.collect {
      case (k: AttributeKey[kt], v) => AttributeEntry(k, v.asInstanceOf[kt])
    }

  private[sbt] def setCond[T](k: AttributeKey[T], opt: Option[T]): AttributeMap =
    opt match {
      case Some(v) => put(k, v)
      case None    => remove(k)
    }

  override def toString = entries.mkString("(", ", ", ")")
}

// type inference required less generality
/** A map entry where `key` is constrained to only be associated with a fixed value of type `T`. */
final case class AttributeEntry[T](key: AttributeKey[T], value: T) {
  override def toString = key.label + ": " + value
}

/** Associates a `metadata` map with `data`. */
final case class Attributed[D](data: D)(val metadata: AttributeMap) {

  /** Retrieves the associated value of `key` from the metadata. */
  def get[T](key: AttributeKey[T]): Option[T] = metadata.get(key)

  /** Defines a mapping `key -> value` in the metadata. */
  def put[T](key: AttributeKey[T], value: T): Attributed[D] =
    Attributed(data)(metadata.put(key, value))

  /** Transforms the data by applying `f`. */
  def map[T](f: D => T): Attributed[T] = Attributed(f(data))(metadata)

}

object Attributed {

  /** Extracts the underlying data from the sequence `in`. */
  def data[T](in: Seq[Attributed[T]]): Seq[T] = in.map(_.data)

  /** Associates empty metadata maps with each entry of `in`.*/
  def blankSeq[T](in: Seq[T]): Seq[Attributed[T]] = in map blank

  /** Associates an empty metadata map with `data`. */
  def blank[T](data: T): Attributed[T] = Attributed(data)(AttributeMap.empty)

}
