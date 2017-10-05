/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import scala.annotation.implicitNotFound

object Remove {
  @implicitNotFound(
    msg = "No implicit for Remove.Value[${A}, ${B}] found,\n  so ${B} cannot be removed from ${A}")
  trait Value[A, B] extends Any {
    def removeValue(a: A, b: B): A
  }
  @implicitNotFound(
    msg = "No implicit for Remove.Values[${A}, ${B}] found,\n  so ${B} cannot be removed from ${A}")
  trait Values[A, -B] extends Any {
    def removeValues(a: A, b: B): A
  }
  trait Sequence[A, -B, T] extends Value[A, T] with Values[A, B]

  implicit def removeSeq[T, V <: T]: Sequence[Seq[T], Seq[V], V] =
    new Sequence[Seq[T], Seq[V], V] {
      def removeValue(a: Seq[T], b: V): Seq[T] = a filterNot b.==
      def removeValues(a: Seq[T], b: Seq[V]): Seq[T] = a diff b
    }
  implicit def removeOption[T]: Sequence[Seq[T], Option[T], Option[T]] =
    new Sequence[Seq[T], Option[T], Option[T]] {
      def removeValue(a: Seq[T], b: Option[T]): Seq[T] = b.fold(a)(a filterNot _.==)
      def removeValues(a: Seq[T], b: Option[T]): Seq[T] = b.fold(a)(a filterNot _.==)
    }
}
