/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import scala.annotation.implicitNotFound

object Remove {
  @implicitNotFound(
    msg = "No implicit for Remove.Value[${A}, ${B}] found,\n  so ${B} cannot be removed from ${A}"
  )
  trait Value[A, B] extends Any {
    def removeValue(a: A, b: B): A
  }
  @implicitNotFound(
    msg = "No implicit for Remove.Values[${A}, ${B}] found,\n  so ${B} cannot be removed from ${A}"
  )
  trait Values[A, -B] extends Any {
    def removeValues(a: A, b: B): A
  }
  trait Sequence[A, -B, T] extends Value[A, T] with Values[A, B]

  given removeSeq[T, V <: T]: Sequence[Seq[T], Seq[V], V] =
    new Sequence[Seq[T], Seq[V], V] {
      def removeValue(a: Seq[T], b: V): Seq[T] = a filterNot b.==
      def removeValues(a: Seq[T], b: Seq[V]): Seq[T] = a diff (b: Seq[T])
    }
  given removeOption[T]: Sequence[Seq[T], Option[T], Option[T]] =
    new Sequence[Seq[T], Option[T], Option[T]] {
      def removeValue(a: Seq[T], b: Option[T]): Seq[T] = b.fold(a)(a filterNot _.==)
      def removeValues(a: Seq[T], b: Option[T]): Seq[T] = b.fold(a)(a filterNot _.==)
    }
  given removeSet[T, V <: T]: Sequence[Set[T], Set[V], V] =
    new Sequence[Set[T], Set[V], V] {
      def removeValue(a: Set[T], b: V): Set[T] = a - b
      def removeValues(a: Set[T], b: Set[V]): Set[T] = a diff (b.toSeq: Seq[T]).toSet
    }
  given removeMap[A, B, X <: A]: Sequence[Map[A, B], Seq[X], X] =
    new Sequence[Map[A, B], Seq[X], X] {
      def removeValue(a: Map[A, B], b: X): Map[A, B] = a - b
      def removeValues(a: Map[A, B], b: Seq[X]): Map[A, B] = a -- b
    }
}
