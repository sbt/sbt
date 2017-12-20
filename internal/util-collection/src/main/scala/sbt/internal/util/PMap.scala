/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.internal.util

import collection.mutable

trait RMap[K[_], V[_]] {
  def apply[T](k: K[T]): V[T]
  def get[T](k: K[T]): Option[V[T]]
  def contains[T](k: K[T]): Boolean
  def toSeq: Seq[(K[_], V[_])]

  def toTypedSeq: Seq[TPair[_]] = toSeq.map {
    case (k: K[t], v) => TPair[t](k, v.asInstanceOf[V[t]])
  }

  def keys: Iterable[K[_]]
  def values: Iterable[V[_]]
  def isEmpty: Boolean

  sealed case class TPair[T](key: K[T], value: V[T])
}

trait IMap[K[_], V[_]] extends (K ~> V) with RMap[K, V] {
  def put[T](k: K[T], v: V[T]): IMap[K, V]
  def remove[T](k: K[T]): IMap[K, V]
  def mapValue[T](k: K[T], init: V[T], f: V[T] => V[T]): IMap[K, V]
  def mapValues[V2[_]](f: V ~> V2): IMap[K, V2]
  def mapSeparate[VL[_], VR[_]](f: V ~> λ[T => Either[VL[T], VR[T]]]): (IMap[K, VL], IMap[K, VR])
}

trait PMap[K[_], V[_]] extends (K ~> V) with RMap[K, V] {
  def update[T](k: K[T], v: V[T]): Unit
  def remove[T](k: K[T]): Option[V[T]]
  def getOrUpdate[T](k: K[T], make: => V[T]): V[T]
  def mapValue[T](k: K[T], init: V[T], f: V[T] => V[T]): V[T]
}

object PMap {
  implicit def toFunction[K[_], V[_]](map: PMap[K, V]): K[_] => V[_] = k => map(k)
  def empty[K[_], V[_]]: PMap[K, V] = new DelegatingPMap[K, V](new mutable.HashMap)
}

object IMap {

  /**
   * Only suitable for K that is invariant in its type parameter.
   * Option and List keys are not suitable, for example,
   *  because None &lt;:&lt; Option[String] and None &lt;: Option[Int].
   */
  def empty[K[_], V[_]]: IMap[K, V] = new IMap0[K, V](Map.empty)

  private[this] class IMap0[K[_], V[_]](backing: Map[K[_], V[_]])
      extends AbstractRMap[K, V]
      with IMap[K, V] {
    def get[T](k: K[T]): Option[V[T]] = (backing get k).asInstanceOf[Option[V[T]]]
    def put[T](k: K[T], v: V[T]) = new IMap0[K, V](backing.updated(k, v))
    def remove[T](k: K[T]) = new IMap0[K, V](backing - k)

    def mapValue[T](k: K[T], init: V[T], f: V[T] => V[T]) =
      put(k, f(this get k getOrElse init))

    def mapValues[V2[_]](f: V ~> V2) =
      new IMap0[K, V2](backing.mapValues(x => f(x)))

    def mapSeparate[VL[_], VR[_]](f: V ~> λ[T => Either[VL[T], VR[T]]]) = {
      val mapped = backing.iterator.map {
        case (k, v) =>
          f(v) match {
            case Left(l)  => Left((k, l))
            case Right(r) => Right((k, r))
          }
      }
      val (l, r) = Util.separateE[(K[_], VL[_]), (K[_], VR[_])](mapped.toList)
      (new IMap0[K, VL](l.toMap), new IMap0[K, VR](r.toMap))
    }

    def toSeq = backing.toSeq
    def keys = backing.keys
    def values = backing.values
    def isEmpty = backing.isEmpty

    override def toString = backing.toString
  }
}

abstract class AbstractRMap[K[_], V[_]] extends RMap[K, V] {
  def apply[T](k: K[T]): V[T] = get(k).get
  def contains[T](k: K[T]): Boolean = get(k).isDefined
}

/**
 * Only suitable for K that is invariant in its type parameter.
 * Option and List keys are not suitable, for example,
 *  because None &lt;:&lt; Option[String] and None &lt;: Option[Int].
 */
class DelegatingPMap[K[_], V[_]](backing: mutable.Map[K[_], V[_]])
    extends AbstractRMap[K, V]
    with PMap[K, V] {
  def get[T](k: K[T]): Option[V[T]] = cast[T](backing.get(k))
  def update[T](k: K[T], v: V[T]): Unit = { backing(k) = v }
  def remove[T](k: K[T]) = cast(backing.remove(k))
  def getOrUpdate[T](k: K[T], make: => V[T]) = cast[T](backing.getOrElseUpdate(k, make))

  def mapValue[T](k: K[T], init: V[T], f: V[T] => V[T]): V[T] = {
    val v = f(this get k getOrElse init)
    update(k, v)
    v
  }

  def toSeq = backing.toSeq
  def keys = backing.keys
  def values = backing.values
  def isEmpty = backing.isEmpty

  private[this] def cast[T](v: V[_]): V[T] = v.asInstanceOf[V[T]]
  private[this] def cast[T](o: Option[V[_]]): Option[V[T]] = o map cast[T]

  override def toString = backing.toString
}
