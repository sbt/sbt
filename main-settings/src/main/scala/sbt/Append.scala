/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.io.File

import scala.annotation.{ compileTimeOnly, implicitNotFound }

import sbt.Def.{ Classpath, Initialize }
import sbt.internal.io.Source
import sbt.internal.util.Attributed
import sbt.io.{ AllPassFilter, NothingFilter }

object Append {
  @implicitNotFound("No Append.Value[${A}, ${B}] found, so ${B} cannot be appended to ${A}")
  trait Value[A, B] {
    def appendValue(a: A, b: B): A
  }

  @implicitNotFound("No Append.Values[${A}, ${B}] found, so ${B} cannot be appended to ${A}")
  trait Values[A, -B] {
    def appendValues(a: A, b: B): A
  }

  // NOTE: the implementations below use SAM conversion short-hand to elide the boilerplate.

  implicit def appendSeq1[T, V <: T]: Value[Seq[T], V] = _ :+ _
  implicit def appendSeqN[T, V <: T]: Values[Seq[T], Seq[V]] = _ ++ _

  implicit def appendSeqImplicit1[T, V](implicit ev: V => T): Value[Seq[T], V] = _ :+ ev(_)
  implicit def appendSeqImplicitN[T, V](implicit ev: V => T): Values[Seq[T], Seq[V]] =
    _ ++ _.map(ev)

  @compileTimeOnly("This can be used in += only.")
  implicit def appendTaskValueSeq[T, V <: T]: Value[Seq[Task[T]], Initialize[Task[V]]] =
    (_, _) => ???

  @compileTimeOnly("This can be used in += only.")
  implicit def appendTaskKeySeq[T, V <: T]: Value[Seq[Task[T]], TaskKey[V]] = (_, _) => ???

  implicit def appendList1[T, V <: T]: Value[List[T], V] = _ :+ _
  implicit def appendListN[T, V <: T]: Values[List[T], List[V]] = _ ::: _

  implicit def appendListImplicit1[T, V](implicit ev: V => T): Value[List[T], V] = _ :+ ev(_)
  implicit def appendListImplicitN[T, V](implicit ev: V => T): Values[List[T], List[V]] =
    _ ::: _.map(ev)

  implicit def appendVectorImplicit1[T, V](implicit ev: V => T): Value[Vector[T], V] = _ :+ ev(_)
  implicit def appendVectorImplicitN[T, V](implicit ev: V => T): Values[Vector[T], Seq[V]] =
    _ ++ _.map(ev)

  implicit def appendString: Value[String, String] = _ + _
  implicit def appendInt: Value[Int, Int] = _ + _
  implicit def appendLong: Value[Long, Long] = _ + _
  implicit def appendDouble: Value[Double, Double] = _ + _

  implicit def appendClasspath1: Value[Classpath, File] = _ :+ Attributed.blank(_)
  implicit def appendClasspathN: Values[Classpath, Seq[File]] = _ ++ Attributed.blankSeq(_)

  implicit def appendSet1[T, V <: T]: Value[Set[T], V] = _ + _
  implicit def appendSetN[T, V <: T]: Values[Set[T], Set[V]] = _ ++ _

  implicit def appendMap1[A, B, X <: A, Y <: B]: Value[Map[A, B], (X, Y)] = _ + _
  implicit def appendMapN[A, B, X <: A, Y <: B, M[K, V] <: Map[K, V]]: Values[Map[A, B], M[X, Y]] =
    _ ++ _

  implicit def appendOption1[T]: Value[Seq[T], Option[T]] = (a, b) => b.fold(a)(a :+ _)
  implicit def appendOptionN[T]: Values[Seq[T], Option[T]] = (a, b) => b.fold(a)(a :+ _)

  implicit def appendSource1: Value[Seq[Source], File] =
    _ :+ new Source(_, AllPassFilter, NothingFilter)

  implicit def appendSourceN: Values[Seq[Source], Seq[File]] =
    _ ++ _.map(new Source(_, AllPassFilter, NothingFilter))

  implicit def appendFunction[A, B]: Value[A => A, A => A] = _.andThen(_)

  implicit def appendSideEffectToFunc[A, B]: Value[A => B, () => Unit] = (f, sideEffect) => {
    f.andThen { b =>
      sideEffect()
      b
    }
  }

  // un-implicit, package-private (and JVM public) the old way of defining these
  @deprecated("No longer required with SAM conversions. Scheduled for removal in 2.0.", "1.3.0")
  private[sbt] trait Sequence[A, -B, T] extends Value[A, T] with Values[A, B]

  private[sbt] def seq[A, B, T](implicit a1: Value[A, T], aN: Values[A, B]): Sequence[A, B, T] =
    new Sequence[A, B, T] {
      def appendValue(a: A, b: T): A = a1.appendValue(a, b)
      def appendValues(a: A, b: B): A = aN.appendValues(a, b)
    }

  private[sbt] def appendSeq[T, V <: T]: Sequence[Seq[T], Seq[V], V] = seq[Seq[T], Seq[V], V]
  private[sbt] def appendSeqImplicit[T, V](implicit ev: V => T): Sequence[Seq[T], Seq[V], V] =
    seq[Seq[T], Seq[V], V]
  private[sbt] def appendList[T, V <: T]: Sequence[List[T], List[V], V] = seq[List[T], List[V], V]
  private[sbt] def appendListImplicit[T, V](implicit ev: V => T): Sequence[List[T], List[V], V] =
    seq[List[T], List[V], V]
  private[sbt] def appendVectorImplicit[T, V](implicit ev: V => T): Sequence[Vector[T], Seq[V], V] =
    seq[Vector[T], Seq[V], V]
  private[sbt] def appendClasspath: Sequence[Classpath, Seq[File], File] =
    seq[Classpath, Seq[File], File]
  private[sbt] def appendSet[T, V <: T]: Sequence[Set[T], Set[V], V] = seq[Set[T], Set[V], V]
  private[sbt] def appendMap[A, B, X <: A, Y <: B]: Sequence[Map[A, B], Map[X, Y], (X, Y)] =
    seq[Map[A, B], Map[X, Y], (X, Y)]
  private[sbt] def appendOption[T]: Sequence[Seq[T], Option[T], Option[T]] =
    seq[Seq[T], Option[T], Option[T]]
  private[sbt] def appendSource: Sequence[Seq[Source], Seq[File], File] =
    seq[Seq[Source], Seq[File], File]
}
