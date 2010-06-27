/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt

object Relation
{
	/** Constructs a new immutable, finite relation that is initially empty. */
	def empty[A,B]: Relation[A,B] = make(Map.empty, Map.empty)
	def make[A,B](forward: Map[A,Set[B]], reverse: Map[B, Set[A]]): Relation[A,B] = new MRelation(forward, reverse)
}
/** Binary relation between A and B.  It is a set of pairs (_1, _2) for _1 in A, _2 in B.  */
trait Relation[A,B]
{
	/** Returns the set of all _2s such that (_1, _2) is in this relation. */
	def forward(_1: A): Set[B]
	/** Returns the set of all _1s such that (_1, _2) is in this relation. */
	def reverse(_2: B): Set[A]
	/** Includes the relation given by `pair`. */
	def +(pair: (A, B)): Relation[A,B]
	/** Includes the relation (a, b). */
	def +(a: A, b: B): Relation[A,B]
	/** Includes the relations (a, b) for all b in bs. */
	def +(a: A, bs: Iterable[B]): Relation[A,B]
	/** Returns the union of the relation r with this relation. */
	def ++(r: Relation[A,B]): Relation[A,B]
	/** Includes the given relations. */
	def ++(rs: Iterable[(A,B)]): Relation[A,B]
	/** Removes all relations (_1, _2) for all _1 in _1s. */
	def --(_1s: Iterable[A]): Relation[A,B]
	/** Removes all `pairs` from this relation. */
	def --(pairs: Traversable[(A,B)]): Relation[A,B]
	/** Removes all pairs (_1, _2) from this relation. */
	def -(_1: A): Relation[A,B]
	/** Removes `pair` from this relation. */
	def -(pair: (A,B)): Relation[A,B]
	/** Returns the set of all _1s such that (_1, _2) is in this relation. */
	def _1s: collection.Set[A]
	/** Returns the set of all _2s such that (_1, _2) is in this relation. */
	def _2s: collection.Set[B]
	
	/** Returns all pairs in this relation.*/
	def all: Traversable[(A,B)]
	
	def forwardMap: Map[A, Set[B]]
	def reverseMap: Map[B, Set[A]]
}
private final class MRelation[A,B](fwd: Map[A, Set[B]], rev: Map[B, Set[A]]) extends Relation[A,B]
{
	type M[X,Y] = Map[X, Set[Y]]

	def forwardMap = fwd
	def reverseMap = rev
	
	def forward(t: A) = get(fwd, t)
	def reverse(t: B) = get(rev, t)

	def _1s = fwd.keySet
	def _2s = rev.keySet
	
	def all: Traversable[(A,B)] = fwd.iterator.flatMap { case (a, bs) => bs.iterator.map( b => (a,b) ) }.toTraversable
	
	def +(pair: (A,B)) = this + (pair._1, Set(pair._2))
	def +(from: A, to: B) = this + (from, Set(to))
	def +(from: A, to: Iterable[B]) =
		new MRelation( add(fwd, from, to), (rev /: to) { (map, t) => add(map, t, Seq(from)) })

	def ++(rs: Iterable[(A,B)]) = ((this: Relation[A,B]) /: rs) { _ + _ }
	def ++(other: Relation[A,B]) = new MRelation[A,B]( combine(fwd, other.forwardMap), combine(rev, other.reverseMap) )

	def --(ts: Iterable[A]): Relation[A,B] = ((this: Relation[A,B]) /: ts) { _ - _ }
	def --(pairs: Traversable[(A,B)]): Relation[A,B] = ((this: Relation[A,B]) /: pairs) { _ - _ }
	def -(pair: (A,B)): Relation[A,B] =
		new MRelation( remove(fwd, pair._1, pair._2), remove(rev, pair._2, pair._1) )
	def -(t: A): Relation[A,B] =
		fwd.get(t) match {
			case Some(rs) =>
				val upRev = (rev /: rs) { (map, r) => remove(map, r, t) }
				new MRelation(fwd - t, upRev)
			case None => this
		}

	private def remove[X,Y](map: M[X,Y], from: X, to: Y): M[X,Y] =
		map.get(from) match {
			case Some(tos) =>
				val newSet = tos - to
				if(newSet.isEmpty) map - from else map.updated(from, newSet)
			case None => map
		}

	private def combine[X,Y](a: M[X,Y], b: M[X,Y]): M[X,Y] =
		(a /: b) { (map, mapping) => add(map, mapping._1, mapping._2) }

	private[this] def add[X,Y](map: M[X,Y], from: X, to: Iterable[Y]): M[X,Y] =
		map.updated(from,  get(map, from) ++ to)

	private[this] def get[X,Y](map: M[X,Y], t: X): Set[Y] = map.getOrElse(t, Set.empty[Y])
	
	override def toString = all.mkString("Relation [", ", ", "]")
}