package sbt
package internal

import scala.collection.immutable.LinearSeq
import scala.language.implicitConversions

class NonEmpty[+A](private val iterable: Iterable[A]) extends AnyVal {
  override def toString = iterable.toString

  def ++[B >: A, That](that: TraversableOnce[B])(implicit bf: CanBuildFrom[A, B, That]): That =
    iterable.++(that)(bf.canBuildFrom)

  def toList: List[A] = iterable.toList

  def init: NonEmpty[A] = new NonEmpty(iterable.init)

  def last: A = iterable.last

  def groupBy[K](f: A => K): scala.collection.immutable.Map[K, NonEmpty[A]] =
    iterable.groupBy(f).mapValues(new NonEmpty(_))

  def grouped(size: Int): Iterator[NonEmpty[A]] =
    iterable.grouped(size).map(new NonEmpty(_))

  def map[B, That](f: A => B)(implicit bf: CanBuildFrom[A, B, That]): That =
    iterable.map(f)(bf.canBuildFrom)

  def scan[B >: A, That](z: B)(op: (B, B) => B)(implicit bf: CanBuildFrom[A, B, That]): That =
    iterable.scan(z)(op)(bf.canBuildFrom)

  def scanRight[B, That](z: B)(op: (A, B) => B)(implicit bf: CanBuildFrom[A, B, That]): That =
    iterable.scanRight(z)(op)(bf.canBuildFrom)

  def unzip[A1, A2](implicit asPair: A => (A1, A2)): (NonEmpty[A1], NonEmpty[A2]) = {
    val (a1, a2) = iterable.unzip(asPair)
    (new NonEmpty(a1), new NonEmpty(a2))
  }

  def zipWithIndex[A1 >: A, That](implicit bf: CanBuildFrom[A, (A1, Int), That]): That =
    iterable.zipWithIndex(bf.canBuildFrom)
  
}

object NonEmpty {

  implicit def fromIterable[A](it: Iterable[A]): Option[NonEmpty[A]] =
    Option(it).filter(_.nonEmpty).map { it => new NonEmpty(
      it match {
        case _: IndexedSeq[_] => it.toIndexedSeq
        case _: LinearSeq[_] | _: Set[_] | _: Map[_, _] => it
        case _ => it.toList
      }
    )}

  def fromTraversable[A](t: Traversable[A]): Option[NonEmpty[A]] = t.toIterable

  def apply[A](head: A, tail: A*): NonEmpty[A] =
    new NonEmpty[A](head +: tail.toList)
}
