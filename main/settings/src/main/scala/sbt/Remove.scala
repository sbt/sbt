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
  sealed trait Sequence[A, -B, T] extends Value[A, T] with Values[A, B]

  implicit def removeSeq[T, V <: T]: Sequence[Seq[T], Seq[V], V] = new Sequence[Seq[T], Seq[V], V] {
    def removeValue(a: Seq[T], b: V): Seq[T] = a filterNot b.==
    def removeValues(a: Seq[T], b: Seq[V]): Seq[T] = a diff b
  }
}
