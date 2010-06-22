/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt

object Relation
{
	/** Constructs a new immutable, finite relation that is initially empty. */
	def empty[T]: Relation[T] = new MRelation[T](Map.empty, Map.empty)
}
/** Binary relation on T.  It is a set of pairs (_1, _2) for _1, _2 in T.  */
trait Relation[T]
{
	/** Returns the set of all _2s such that (_1, _2) is in this relation. */
	def forward(_1: T): Set[T]
	/** Returns the set of all _1s such that (_1, _2) is in this relation. */
	def reverse(_2: T): Set[T]
	/** Includes the relation given by `pair`. */
	def +(pair: (T, T)): Relation[T]
	/** Includes the relation (a, b). */
	def +(a: T, b: T): Relation[T]
	/** Includes the relations (a, b) for all b in bs. */
	def +(a: T, bs: Iterable[T]): Relation[T]
	/** Returns the union of the relation r with this relation. */
	def ++(r: Relation[T]): Relation[T]
	/** Includes the given relations. */
	def ++(rs: Iterable[(T,T)]): Relation[T]
	/** Removes all relations (_1, _2) for all _1 in _1s. */
	def --(_1s: Iterable[T]): Relation[T]
	/** Removes all `pairs` from this relation. */
	def --(pairs: Traversable[(T,T)]): Relation[T]
	/** Removes all pairs (_1, _2) from this relation. */
	def -(_1: T): Relation[T]
	/** Removes `pair` from this relation. */
	def -(pair: (T,T)): Relation[T]
	/** Returns the set of all _1s such that (_1, _2) is in this relation. */
	def _1s: collection.Set[T]
	/** Returns the set of all _2s such that (_1, _2) is in this relation. */
	def _2s: collection.Set[T]
	
	/** Returns all pairs in this relation.*/
	def all: Traversable[(T,T)]
	
	def forwardMap: Map[T, Set[T]]
	def reverseMap: Map[T, Set[T]]
}
private final class MRelation[T](fwd: Map[T, Set[T]], rev: Map[T, Set[T]]) extends Relation[T]
{
	type M = Map[T, Set[T]]

	def forwardMap = fwd
	def reverseMap = rev
	
	def forward(t: T) = get(fwd, t)
	def reverse(t: T) = get(rev, t)

	def _1s = fwd.keySet
	def _2s = rev.keySet
	
	def all: Traversable[(T,T)] = fwd.iterator.flatMap { case (a, bs) => bs.iterator.map( b => (a,b) ) }.toTraversable
	
	def +(pair: (T, T)): Relation[T] = this + (pair._1, Set(pair._2))
	def +(from: T, to: T): Relation[T] = this + (from, Set(to))
	def +(from: T, to: Iterable[T]): Relation[T] =
		new MRelation( add(fwd, from, to), (rev /: to) { (map, t) => add(map, t, Seq(from)) })

	def ++(rs: Iterable[(T,T)]): Relation[T] = ((this: Relation[T]) /: rs) { _ + _ }
	def ++(other: Relation[T]): Relation[T] = new MRelation[T]( combine(fwd, other.forwardMap), combine(rev, other.reverseMap) )

	def --(ts: Iterable[T]): Relation[T] = ((this: Relation[T]) /: ts) { _ - _ }
	def --(pairs: Traversable[(T,T)]): Relation[T] = ((this: Relation[T]) /: pairs) { _ - _ }
	def -(pair: (T,T)): Relation[T] =
		new MRelation( remove(fwd, pair._1, pair._2), remove(rev, pair._2, pair._1) )
	def -(t: T): Relation[T] =
		fwd.get(t) match {
			case Some(rs) =>
				val upRev = (rev /: rs) { (map, r) => remove(map, r, t) }
				new MRelation(fwd - t, upRev)
			case None => this
		}

	private def remove(map: M, from: T, to: T): M =
		map.get(from) match {
			case Some(tos) =>
				val newSet = tos - to
				if(newSet.isEmpty) map - from else map.updated(from, newSet)
			case None => map
		}

	private def combine(a: M, b: M): M =
		(a /: b) { (map, mapping) => add(map, mapping._1, mapping._2) }

	private[this] def add(map: M, from: T, to: Iterable[T]): M =
		map.updated(from,  get(map, from) ++ to)

	private[this] def get(map: M, t: T): Set[T] = map.getOrElse(t, Set.empty[T])
	
	override def toString = all.mkString("Relation [", ", ", "]")
}