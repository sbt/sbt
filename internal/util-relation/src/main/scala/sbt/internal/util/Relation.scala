/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import Relation._

object Relation {

  /** Constructs a new immutable, finite relation that is initially empty. */
  def empty[A, B]: Relation[A, B] = make(Map.empty, Map.empty)

  /**
   * Constructs a [[Relation]] from underlying `forward` and `reverse` representations, without
   * checking that they are consistent. This is a low-level constructor and the alternatives
   * [[empty]] and [[reconstruct]] should be preferred.
   */
  def make[A, B](forward: Map[A, Set[B]], reverse: Map[B, Set[A]]): Relation[A, B] =
    new MRelation(forward, reverse)

  /**
   * Constructs a relation such that for every entry `_1 -> _2s` in `forward` and every `_2` in
   * `_2s`, `(_1, _2)` is in the relation.
   */
  def reconstruct[A, B](forward: Map[A, Set[B]]): Relation[A, B] = {
    val reversePairs = for ((a, bs) <- forward.view; b <- bs.view) yield (b, a)
    val reverse = reversePairs.foldLeft(Map.empty[B, Set[A]]) { case (m, (b, a)) =>
      add(m, b, a :: Nil)
    }
    make(forward filter { case (a, bs) => bs.nonEmpty }, reverse)
  }

  def merge[A, B](rels: Iterable[Relation[A, B]]): Relation[A, B] =
    rels.foldLeft(Relation.empty[A, B])(_ ++ _)

  private[sbt] def remove[X, Y](map: M[X, Y], from: X, to: Y): M[X, Y] =
    map.get(from) match {
      case Some(tos) =>
        val newSet = tos - to
        if (newSet.isEmpty) map - from else map.updated(from, newSet)
      case None => map
    }

  private[sbt] def combine[X, Y](a: M[X, Y], b: M[X, Y]): M[X, Y] =
    b.foldLeft(a)((map, mapping) => add(map, mapping._1, mapping._2))

  private[sbt] def add[X, Y](map: M[X, Y], from: X, to: Iterable[Y]): M[X, Y] =
    map.updated(from, get(map, from) ++ to)

  private[sbt] def get[X, Y](map: M[X, Y], t: X): Set[Y] = map.getOrElse(t, Set.empty[Y])

  private[sbt] type M[X, Y] = Map[X, Set[Y]]

  /** when both parameters taken by relation are the same type, switch calls a function on them. */
  private[sbt] def switch[X, Y](relation: Relation[X, X], f: X => Y): Relation[Y, Y] = {
    val forward = relation.forwardMap.map { case (first, second) =>
      f(first) -> second.map(f)
    }
    val reverse = relation.reverseMap.map { case (first, second) =>
      f(first) -> second.map(f)
    }
    make(forward, reverse)
  }
}

/** Binary relation between A and B.  It is a set of pairs (_1, _2) for _1 in A, _2 in B. */
trait Relation[A, B] {

  /** Returns the set of all `_2`s such that `(_1, _2)` is in this relation. */
  def forward(_1: A): Set[B]

  /** Returns the set of all `_1`s such that `(_1, _2)` is in this relation. */
  def reverse(_2: B): Set[A]

  /** Includes `pair` in the relation. */
  def +(pair: (A, B)): Relation[A, B]

  /** Includes `(a, b)` in the relation. */
  def +(a: A, b: B): Relation[A, B]

  /** Includes in the relation `(a, b)` for all `b` in `bs`. */
  def +(a: A, bs: Iterable[B]): Relation[A, B]

  /** Returns the union of the relation `r` with this relation. */
  def ++(r: Relation[A, B]): Relation[A, B]

  /** Includes the given pairs in this relation. */
  def ++(rs: Iterable[(A, B)]): Relation[A, B]

  /** Removes all elements `(_1, _2)` for all `_1` in `_1s` from this relation. */
  def --(_1s: Iterable[A]): Relation[A, B]

  /** Removes all `pairs` from this relation. */
  def --(pairs: IterableOnce[(A, B)]): Relation[A, B]

  /** Removes all `relations` from this relation. */
  def --(relations: Relation[A, B]): Relation[A, B]

  /** Removes all pairs `(_1, _2)` from this relation. */
  def -(_1: A): Relation[A, B]

  /** Removes `pair` from this relation. */
  def -(pair: (A, B)): Relation[A, B]

  /** Returns the set of all `_1`s such that `(_1, _2)` is in this relation. */
  def _1s: Set[A]

  /** Returns the set of all `_2`s such that `(_1, _2)` is in this relation. */
  def _2s: Set[B]

  /** Returns the number of pairs in this relation */
  def size: Int

  /** Returns true iff `(a,b)` is in this relation */
  def contains(a: A, b: B): Boolean

  /** Returns a relation with only pairs `(a,b)` for which `f(a,b)` is true. */
  def filter(f: (A, B) => Boolean): Relation[A, B]

  /**
   * Returns a pair of relations: the first contains only pairs `(a,b)` for which `f(a,b)` is true
   * and the other only pairs `(a,b)` for which `f(a,b)` is false.
   */
  def partition(f: (A, B) => Boolean): (Relation[A, B], Relation[A, B])

  /** Partitions this relation into a map of relations according to some discriminator function. */
  def groupBy[K](discriminator: ((A, B)) => K): Map[K, Relation[A, B]]

  /** Returns all pairs in this relation. */
  def all: Iterable[(A, B)]

  /**
   * Represents this relation as a `Map` from a `_1` to the set of `_2`s such that `(_1, _2)` is in
   * this relation.
   *
   * Specifically, there is one entry for each `_1` such that `(_1, _2)` is in this relation for
   * some `_2`. The value associated with a given `_1` is the set of all `_2`s such that `(_1, _2)`
   * is in this relation.
   */
  def forwardMap: Map[A, Set[B]]

  /**
   * Represents this relation as a `Map` from a `_2` to the set of `_1`s such that `(_1, _2)` is in
   * this relation.
   *
   * Specifically, there is one entry for each `_2` such that `(_1, _2)` is in this relation for
   * some `_1`. The value associated with a given `_2` is the set of all `_1`s such that `(_1, _2)`
   * is in this relation.
   */
  def reverseMap: Map[B, Set[A]]
}

// Note that we assume without checking that fwd and rev are consistent.
private final class MRelation[A, B](fwd: Map[A, Set[B]], rev: Map[B, Set[A]])
    extends Relation[A, B] {
  def forwardMap = fwd
  def reverseMap = rev

  def forward(t: A) = get(fwd, t)
  def reverse(t: B) = get(rev, t)

  def _1s = fwd.keySet
  def _2s = rev.keySet

  def size = (fwd.valuesIterator map (_.size)).sum

  def all: Iterable[(A, B)] =
    fwd.iterator.flatMap { case (a, bs) => bs.iterator.map(b => (a, b)) }.to(Iterable)

  def +(pair: (A, B)) = this + (pair._1, Set(pair._2))
  def +(from: A, to: B) = this + (from, to :: Nil)
  def +(from: A, to: Iterable[B]) =
    if (to.isEmpty) this
    else new MRelation(add(fwd, from, to), to.foldLeft(rev)((map, t) => add(map, t, from :: Nil)))

  def ++(rs: Iterable[(A, B)]) = rs.foldLeft(this: Relation[A, B]) { _ + _ }
  def ++(other: Relation[A, B]): Relation[A, B] =
    new MRelation[A, B](combine(fwd, other.forwardMap), combine(rev, other.reverseMap))

  def --(ts: Iterable[A]): Relation[A, B] = ts.foldLeft(this: Relation[A, B]) { _ - _ }
  def --(pairs: IterableOnce[(A, B)]): Relation[A, B] =
    pairs.iterator.foldLeft(this: Relation[A, B])(_ - _)
  def --(relations: Relation[A, B]): Relation[A, B] = --(relations.all)

  def -(pair: (A, B)): Relation[A, B] =
    new MRelation(remove(fwd, pair._1, pair._2), remove(rev, pair._2, pair._1))

  def -(t: A): Relation[A, B] =
    fwd.get(t) match {
      case Some(rs) =>
        val upRev = rs.foldLeft(rev)((map, r) => remove(map, r, t))
        new MRelation(fwd - t, upRev)
      case None => this
    }

  def filter(f: (A, B) => Boolean): Relation[A, B] = Relation.empty[A, B] ++ all.filter(f.tupled)

  def partition(f: (A, B) => Boolean): (Relation[A, B], Relation[A, B]) = {
    val (y, n) = all.partition(f.tupled)
    (Relation.empty[A, B] ++ y, Relation.empty[A, B] ++ n)
  }

  def groupBy[K](discriminator: ((A, B)) => K): Map[K, Relation[A, B]] =
    all.groupBy(discriminator).view.mapValues { Relation.empty[A, B] ++ _ }.toMap

  def contains(a: A, b: B): Boolean = forward(a)(b)

  override def equals(other: Any) = other match {
    // We assume that the forward and reverse maps are consistent, so we only use the forward map
    // for equality. Note that key -> Empty is semantically the same as key not existing.
    case o: MRelation[?, ?] =>
      forwardMap.filterNot(_._2.isEmpty) == o.forwardMap.filterNot(_._2.isEmpty)
    case _ => false
  }

  override def hashCode = fwd.filterNot(_._2.isEmpty).hashCode()

  override def toString =
    all.map { case (a, b) => s"$a -> $b" }.mkString("Relation [", ", ", "]")
}
