/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import Types._
import scala.reflect.ClassTag
import sbt.util.OptJsonWriter
import sjsonnew.*

enum KeyTag[A]:
  case Setting[A](typeArg: Class[?]) extends KeyTag[A]
  case Task[A](typeArg: Class[?]) extends KeyTag[A]
  case SeqTask[A](typeArg: Class[?]) extends KeyTag[A]
  case InputTask[A](typeArg: Class[?]) extends KeyTag[A]

  override def toString: String = this match
    case Setting(typeArg)   => typeArg.toString
    case Task(typeArg)      => s"Task[$typeArg]"
    case SeqTask(typeArg)   => s"Task[Seq[$typeArg]]"
    case InputTask(typeArg) => s"InputTask[$typeArg]"

  def typeArg: Class[?]
  def isSetting: Boolean = isInstanceOf[Setting[?]]
  def isTaskOrInputTask: Boolean = !isSetting
end KeyTag

object KeyTag:
  given [A: ClassTag]: KeyTag[A] = Setting[A](summon[ClassTag[A]].runtimeClass)

// A must be invariant to work properly.
//  Because it is sealed and the only instances go through AttributeKey.apply,
//  a single AttributeKey instance cannot conform to AttributeKey[A] for different As

sealed trait AttributeKey[A]:

  /** The runtime evidence for `A`. */
  def tag: KeyTag[A]

  /** The label is the identifier for the key and is camelCase by convention. */
  def label: String

  /** An optional, brief description of the key. */
  def description: Option[String]

  /**
   * In environments that support delegation, looking up this key when it has no associated value
   * will delegate to the values associated with these keys. The delegation proceeds in order the
   * keys are returned here.
   */
  def extend: Seq[AttributeKey[_]]

  /**
   * Specifies whether this key is a local, anonymous key (`true`) or not (`false`). This is
   * typically only used for programmatic, intermediate keys that should not be referenced outside
   * of a specific scope.
   */
  def isLocal: Boolean

  /** Identifies the relative importance of a key among other keys. */
  def rank: Int

  def optJsonWriter: OptJsonWriter[A]

end AttributeKey

opaque type StringAttributeKey = String
object StringAttributeKey:
  def apply(s: String): StringAttributeKey = s
end StringAttributeKey

private[sbt] abstract class SharedAttributeKey[A] extends AttributeKey[A]:
  override final def toString = label
  override final def hashCode = label.hashCode
  override final def equals(o: Any) =
    (this eq o.asInstanceOf[AnyRef]) || (o match {
      case a: SharedAttributeKey[t] => a.label == this.label && a.tag == this.tag
      case _                        => false
    })
  final def isLocal: Boolean = false
end SharedAttributeKey

object AttributeKey {
  def apply[A: KeyTag: OptJsonWriter](name: String): AttributeKey[A] =
    make(name, None, Nil, Int.MaxValue)

  def apply[A: KeyTag: OptJsonWriter](name: String, rank: Int): AttributeKey[A] =
    make(name, None, Nil, rank)

  def apply[A: KeyTag: OptJsonWriter](name: String, description: String): AttributeKey[A] =
    apply(name, description, Nil)

  def apply[A: KeyTag: OptJsonWriter](
      name: String,
      description: String,
      rank: Int
  ): AttributeKey[A] =
    apply(name, description, Nil, rank)

  def apply[A: KeyTag: OptJsonWriter](
      name: String,
      description: String,
      extend: Seq[AttributeKey[_]]
  ): AttributeKey[A] =
    apply(name, description, extend, Int.MaxValue)

  def apply[A: KeyTag: OptJsonWriter](
      name: String,
      description: String,
      extend: Seq[AttributeKey[_]],
      rank: Int
  ): AttributeKey[A] =
    make(name, Some(description), extend, rank)

  private[sbt] def copyWithRank[A](a: AttributeKey[A], rank: Int): AttributeKey[A] =
    make(a.label, a.description, a.extend, rank)(using a.tag, a.optJsonWriter)

  private[this] def make[A: KeyTag: OptJsonWriter](
      name: String,
      description0: Option[String],
      extend0: Seq[AttributeKey[_]],
      rank0: Int
  ): AttributeKey[A] =
    new SharedAttributeKey[A]:
      require(
        name.headOption.exists(_.isLower),
        s"A named attribute key must start with a lowercase letter: $name"
      )

      override def tag: KeyTag[A] = summon
      override val label: String = Util.hyphenToCamel(name)
      override def description: Option[String] = description0
      override def extend: Seq[AttributeKey[_]] = extend0
      override def rank: Int = rank0
      override def optJsonWriter: OptJsonWriter[A] = summon

  private[sbt] def local[A: KeyTag: OptJsonWriter]: AttributeKey[A] =
    new AttributeKey[A]:
      override def tag: KeyTag[A] = summon
      override def label: String = LocalLabel
      override def description: Option[String] = None
      override def extend: Seq[AttributeKey[_]] = Nil
      override def toString = label
      override def isLocal: Boolean = true
      override def rank: Int = Int.MaxValue
      override val optJsonWriter: OptJsonWriter[A] = summon

  private[sbt] final val LocalLabel = "$" + "local"
}

/**
 * An immutable map where a key is the tuple `(String,T)` for a fixed type `T` and can only be
 * associated with values of type `T`. It is therefore possible for this map to contain mappings for
 * keys with the same label but different types. Excluding this possibility is the responsibility of
 * the client if desired.
 */
trait AttributeMap {

  /**
   * Gets the value of type `T` associated with the key `k`. If a key with the same label but
   * different type is defined, this method will fail.
   */
  def apply[T](k: AttributeKey[T]): T

  /**
   * Gets the value of type `T` associated with the key `k` or `None` if no value is associated. If
   * a key with the same label but a different type is defined, this method will return `None`.
   */
  def get[T](k: AttributeKey[T]): Option[T]

  /**
   * Returns this map without the mapping for `k`. This method will not remove a mapping for a key
   * with the same label but a different type.
   */
  def remove[T](k: AttributeKey[T]): AttributeMap

  /**
   * Returns true if this map contains a mapping for `k`. If a key with the same label but a
   * different type is defined in this map, this method will return `false`.
   */
  def contains[T](k: AttributeKey[T]): Boolean

  /**
   * Adds the mapping `k -> value` to this map, replacing any existing mapping for `k`. Any mappings
   * for keys with the same label but different types are unaffected.
   */
  def put[T](k: AttributeKey[T], value: T): AttributeMap

  /**
   * All keys with defined mappings. There may be multiple keys with the same `label`, but different
   * types.
   */
  def keys: Iterable[AttributeKey[_]]

  /**
   * Adds the mappings in `o` to this map, with mappings in `o` taking precedence over existing
   * mappings.
   */
  def ++(o: Iterable[AttributeEntry[_]]): AttributeMap

  /**
   * Combines the mappings in `o` with the mappings in this map, with mappings in `o` taking
   * precedence over existing mappings.
   */
  def ++(o: AttributeMap): AttributeMap

  /**
   * All mappings in this map. The [[AttributeEntry]] type preserves the typesafety of mappings,
   * although the specific types are unknown.
   */
  def entries: Iterable[AttributeEntry[_]]

  /** `true` if there are no mappings in this map, `false` if there are. */
  def isEmpty: Boolean

  /**
   * Adds the mapping `k -> opt.get` if opt is Some. Otherwise, it returns this map without the
   * mapping for `k`.
   */
  private[sbt] def setCond[T](k: AttributeKey[T], opt: Option[T]): AttributeMap
}

object AttributeMap {

  /** An [[AttributeMap]] without any mappings. */
  val empty: AttributeMap = new BasicAttributeMap(Map.empty)

  /** Constructs an [[AttributeMap]] containing the given `entries`. */
  def apply(entries: Iterable[AttributeEntry[_]]): AttributeMap = empty ++ entries

  /** Constructs an [[AttributeMap]] containing the given `entries`. */
  def apply(entries: AttributeEntry[_]*): AttributeMap = empty ++ entries

  /** Presents an `AttributeMap` as a natural transformation. */
  // implicit def toNatTrans(map: AttributeMap): AttributeKey ~> Id = λ[AttributeKey ~> Id](map(_))
}

private class BasicAttributeMap(private val backing: Map[AttributeKey[_], Any])
    extends AttributeMap {

  def isEmpty: Boolean = backing.isEmpty
  def apply[T](k: AttributeKey[T]) = backing(k).asInstanceOf[T]
  def get[T](k: AttributeKey[T]) = backing.get(k).asInstanceOf[Option[T]]
  def remove[T](k: AttributeKey[T]): AttributeMap = new BasicAttributeMap(backing - k)
  def contains[T](k: AttributeKey[T]) = backing.contains(k)

  def put[T](k: AttributeKey[T], value: T): AttributeMap =
    new BasicAttributeMap(backing.updated(k, value: Any))

  def keys: Iterable[AttributeKey[_]] = backing.keys

  def ++(o: Iterable[AttributeEntry[_]]): AttributeMap =
    new BasicAttributeMap(o.foldLeft(backing)((b, e) => b.updated(e.key, e.value: Any)))

  def ++(o: AttributeMap): AttributeMap = o match {
    case bam: BasicAttributeMap =>
      new BasicAttributeMap(Map(backing.toSeq ++ bam.backing.toSeq: _*))
    case _ => o ++ this
  }

  def entries: Iterable[AttributeEntry[_]] =
    backing.collect { case (k: AttributeKey[kt], v) =>
      AttributeEntry(k, v.asInstanceOf[kt])
    }

  private[sbt] def setCond[T](k: AttributeKey[T], opt: Option[T]): AttributeMap =
    opt match {
      case Some(v) => put(k, v)
      case None    => remove(k)
    }

  override def toString = entries.mkString("(", ", ", ")")
}

/**
 * An immutable map where both key and value are String.
 */
type StringAttributeMap = scala.collection.immutable.Map[StringAttributeKey, String]

// type inference required less generality
/** A map entry where `key` is constrained to only be associated with a fixed value of type `T`. */
final case class AttributeEntry[T](key: AttributeKey[T], value: T) {
  override def toString = key.label + ": " + value
}

/** Associates a `metadata` map with `data`. */
final case class Attributed[A1](data: A1)(val metadata: StringAttributeMap):
  /** Retrieves the associated value of `key` from the metadata. */
  def get(key: StringAttributeKey): Option[String] = metadata.get(key)

  /** Defines a mapping `key -> value` in the metadata. */
  def put(key: StringAttributeKey, value: String): Attributed[A1] =
    Attributed(data)(metadata.updated(key, value))

  /** Transforms the data by applying `f`. */
  def map[A2](f: A1 => A2): Attributed[A2] = Attributed(f(data))(metadata)
end Attributed

object Attributed:
  /** Extracts the underlying data from the sequence `in`. */
  def data[T](in: Seq[Attributed[T]]): Seq[T] = in.map(_.data)

  /** Associates empty metadata maps with each entry of `in`. */
  def blankSeq[T](in: Seq[T]): Seq[Attributed[T]] = in map blank

  /** Associates an empty metadata map with `data`. */
  def blank[T](data: T): Attributed[T] = Attributed(data)(Map.empty)

  import sjsonnew.BasicJsonProtocol.*
  given JsonFormat[StringAttributeMap] = projectFormat(
    (m: StringAttributeMap) =>
      m.toSeq.map: entry =>
        (entry._1.toString, entry._2),
    (entries: Seq[(String, String)]) =>
      Map((entries.map: entry =>
        (StringAttributeKey(entry._1), entry._2)): _*),
  )

  given [A1: ClassTag: JsonFormat]
      : IsoLList.Aux[Attributed[A1], A1 :*: StringAttributeMap :*: LNil] =
    LList.iso(
      { (a: Attributed[A1]) =>
        ("data", a.data) :*: ("metadata", a.metadata) :*: LNil
      },
      { (in: A1 :*: StringAttributeMap :*: LNil) =>
        Attributed(in.head)(in.tail.head)
      }
    )
end Attributed
