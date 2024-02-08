/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import collection.mutable

trait RMap[K[_], V[_]] {
  def apply[T](k: K[T]): V[T]
  def get[T](k: K[T]): Option[V[T]]
  def contains[T](k: K[T]): Boolean
  def toSeq: Seq[(K[Any], V[Any])]

  def toTypedSeq: Seq[TPair[_]] = toSeq.map { case (k: K[t], v) =>
    TPair[t](k, v.asInstanceOf[V[t]])
  }

  def keys: Iterable[K[Any]]
  def values: Iterable[V[Any]]
  def isEmpty: Boolean

  sealed case class TPair[T](key: K[T], value: V[T])
}

trait IMap[K[_], V[_]] extends RMap[K, V] {
  def put[T](k: K[T], v: V[T]): IMap[K, V]
  def remove[T](k: K[T]): IMap[K, V]
  def mapValue[T](k: K[T], init: V[T], f: V[T] => V[T]): IMap[K, V]
  def mapValues[V2[_]](f: [A] => V[A] => V2[A]): IMap[K, V2]
}

trait PMap[K[_], V[_]] extends RMap[K, V] {
  def update[T](k: K[T], v: V[T]): Unit
  def remove[T](k: K[T]): Option[V[T]]
  def getOrUpdate[T](k: K[T], make: => V[T]): V[T]
  def mapValue[T](k: K[T], init: V[T], f: V[T] => V[T]): V[T]
}

object PMap {
  // implicit def toFunction[K[_], V[_]](map: PMap[K, V]): [A] => K[A] => V[A] =
  //   [A] => (k: K[A]) => map.apply[A](k)

  given [K[_], V[_]]: Conversion[PMap[K, V], [A] => (K[A]) => V[A]] =
    new Conversion[PMap[K, V], [A] => K[A] => V[A]]:
      def apply(map: PMap[K, V]): [A] => K[A] => V[A] =
        [A] => (k: K[A]) => map.apply[A](k)
  def empty[K[_], V[_]]: PMap[K, V] = new DelegatingPMap[K, V](new mutable.HashMap)
}

object IMap {

  /**
   * Only suitable for K that is invariant in its type parameter. Option and List keys are not
   * suitable, for example, because None &lt;:&lt; Option[String] and None &lt;: Option[Int].
   */
  def empty[K[_], V[_]]: IMap[K, V] = new IMap0[K, V](Map.empty)

  private[sbt] def fromJMap[K[_], V[_]](map: java.util.Map[K[Any], V[Any]]): IMap[K, V] =
    new IMap0[K, V](new WrappedMap[K[Any], V[Any]](map))

  private[sbt] class IMap0[K[_], V[_]](val backing: Map[K[Any], V[Any]])
      extends AbstractRMap[K, V]
      with IMap[K, V] {
    def get[T](k: K[T]): Option[V[T]] =
      (backing get k.asInstanceOf).asInstanceOf[Option[V[T]]]
    def put[T](k: K[T], v: V[T]) =
      new IMap0[K, V](backing.updated(k.asInstanceOf, v.asInstanceOf))
    def remove[T](k: K[T]) = new IMap0[K, V](backing - k.asInstanceOf)

    def mapValue[T](k: K[T], init: V[T], f: V[T] => V[T]) =
      put(k, f(this get k getOrElse init))

    def mapValues[V2[_]](f: [A] => V[A] => V2[A]) =
      new IMap0[K, V2](Map(backing.iterator.map { case (k, v) =>
        k -> f(v.asInstanceOf[V[Any]])
      }.toArray: _*))

    def toSeq = backing.toSeq.asInstanceOf[Seq[(K[Any], V[Any])]]
    def keys = backing.keys.asInstanceOf[Iterable[K[Any]]]
    def values = backing.values.asInstanceOf[Iterable[V[Any]]]
    def isEmpty = backing.isEmpty

    override def toString = backing.toString
  }
}

abstract class AbstractRMap[K[_], V[_]] extends RMap[K, V] {
  def apply[T](k: K[T]): V[T] = get(k).get
  def contains[T](k: K[T]): Boolean = get(k).isDefined
}

/**
 * Only suitable for K that is invariant in its type parameter. Option and List keys are not
 * suitable, for example, because None &lt;:&lt; Option[String] and None &lt;: Option[Int].
 */
class DelegatingPMap[K[_], V[_]](backing: mutable.Map[K[Any], V[Any]])
    extends AbstractRMap[K, V]
    with PMap[K, V] {
  def get[T](k: K[T]): Option[V[T]] = cast[T](backing.get(k.asInstanceOf))
  def update[T](k: K[T], v: V[T]): Unit = { backing(k.asInstanceOf) = v.asInstanceOf }
  def remove[T](k: K[T]) = cast(backing.remove(k.asInstanceOf))
  def getOrUpdate[T](k: K[T], make: => V[T]) =
    cast[T](backing.getOrElseUpdate(k.asInstanceOf, make.asInstanceOf))

  def mapValue[T](k: K[T], init: V[T], f: V[T] => V[T]): V[T] = {
    val v = f(this get k getOrElse init)
    update(k, v)
    v
  }

  def toSeq = backing.toSeq.asInstanceOf[Seq[(K[Any], V[Any])]]
  def keys = backing.keys.asInstanceOf[Iterable[K[Any]]]
  def values = backing.values.asInstanceOf[Iterable[V[Any]]]
  def isEmpty = backing.isEmpty

  private[this] def cast[A](v: V[Any]): V[A] = v.asInstanceOf[V[A]]
  private[this] def cast[A](o: Option[V[Any]]): Option[V[A]] = o map cast[A]

  override def toString = backing.toString
}
