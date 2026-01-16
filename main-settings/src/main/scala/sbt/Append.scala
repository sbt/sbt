/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
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
import xsbti.HashedVirtualFileRef

object Append:
  @implicitNotFound("No Append.Value[${A1}, ${A2}] found, so ${A2} cannot be appended to ${A1}")
  trait Value[A1, A2]:
    def appendValue(a1: A1, a2: A2): A1
  end Value

  @implicitNotFound("No Append.Values[${A1}, ${A2}] found, so ${A2} cannot be appended to ${A1}")
  trait Values[A1, -A2]:
    def appendValues(a1: A1, a2: A2): A1
  end Values

  trait Sequence[A1, -A2, A3] extends Value[A1, A3] with Values[A1, A2]

  given appendSeq[T, V <: T]: Sequence[Seq[T], Seq[V], V] =
    new Sequence[Seq[T], Seq[V], V] {
      def appendValues(a: Seq[T], b: Seq[V]): Seq[T] = a ++ (b: Seq[T])
      def appendValue(a: Seq[T], b: V): Seq[T] = a :+ (b: T)
    }

  given appendSeqImplicit[A1, V](using ev: Conversion[V, A1]): Sequence[Seq[A1], Seq[V], V] with
    override def appendValues(a: Seq[A1], b: Seq[V]): Seq[A1] = a ++ b.map(x => (x: A1))
    override def appendValue(a: Seq[A1], b: V): Seq[A1] = a :+ (b: A1)

  @compileTimeOnly("This can be used in += only.")
  given appendTaskValueSeq[T, V <: T]: Value[Seq[Task[T]], Initialize[Task[V]]] =
    (_, _) => ??? // SAM conversion.  This implementation is rewritten by sbt's macros too.

  @compileTimeOnly("This can be used in += only.")
  given appendTaskKeySeq[T, V <: T]: Value[Seq[Task[T]], TaskKey[V]] = (_, _) => ??? // SAM

  given appendList[T, V <: T]: Sequence[List[T], List[V], V] =
    new Sequence[List[T], List[V], V] {
      def appendValues(a: List[T], b: List[V]): List[T] = a ::: (b: List[T])
      def appendValue(a: List[T], b: V): List[T] = a :+ (b: T)
    }

  given appendListImplicit[A1, V](using ev: Conversion[V, A1]): Sequence[List[A1], List[V], V] with
    override def appendValues(a: List[A1], b: List[V]): List[A1] = a ++ b.map(x => (x: A1))
    override def appendValue(a: List[A1], b: V): List[A1] = a :+ (b: A1)

  given appendVectorImplicit[A1, V](using ev: Conversion[V, A1]): Sequence[Vector[A1], Vector[V], V]
  with
    override def appendValues(a: Vector[A1], b: Vector[V]): Vector[A1] = a ++ b.map(x => (x: A1))
    override def appendValue(a: Vector[A1], b: V): Vector[A1] = a :+ (b: A1)

  // psst... these are implemented with SAM conversions
  given appendString: Value[String, String] = _ + _
  given appendInt: Value[Int, Int] = _ + _
  given appendLong: Value[Long, Long] = _ + _
  given appendDouble: Value[Double, Double] = _ + _

  given Sequence[Classpath, Seq[HashedVirtualFileRef], HashedVirtualFileRef] with
    override def appendValues(a: Classpath, b: Seq[HashedVirtualFileRef]): Classpath =
      a ++ Attributed.blankSeq(b)
    override def appendValue(a: Classpath, b: HashedVirtualFileRef): Classpath =
      a :+ Attributed.blank(b)

  given appendSet[T, V <: T]: Sequence[Set[T], Set[V], V] =
    new Sequence[Set[T], Set[V], V] {
      def appendValues(a: Set[T], b: Set[V]): Set[T] = a ++ (b.toSeq: Seq[T]).toSet
      def appendValue(a: Set[T], b: V): Set[T] = a + (b: T)
    }

  given appendMap[A, B, X <: A, Y <: B]: Sequence[Map[A, B], Map[X, Y], (X, Y)] =
    new Sequence[Map[A, B], Map[X, Y], (X, Y)] {
      def appendValues(a: Map[A, B], b: Map[X, Y]): Map[A, B] =
        (a.toSeq ++ (b.toSeq: Seq[(A, B)])).toMap
      def appendValue(a: Map[A, B], b: (X, Y)): Map[A, B] = a + (b: (A, B))
    }

  given appendOption[T]: Sequence[Seq[T], Option[T], Option[T]] =
    new Sequence[Seq[T], Option[T], Option[T]] {
      def appendValue(a: Seq[T], b: Option[T]): Seq[T] = b.fold(a)(a :+ _)
      def appendValues(a: Seq[T], b: Option[T]): Seq[T] = b.fold(a)(a :+ _)
    }

  given appendSource: Sequence[Seq[Source], Seq[File], File] =
    new Sequence[Seq[Source], Seq[File], File] {
      def appendValue(a: Seq[Source], b: File): Seq[Source] = appendValues(a, Seq(b))
      def appendValues(a: Seq[Source], b: Seq[File]): Seq[Source] =
        a ++ b.map { f =>
          // Globs only accept their own base if the depth parameter is set to -1. The conversion
          // from Source to Glob never sets the depth to -1, which causes individual files
          // added via `watchSource += ...` to not trigger a build when they are modified. Since
          // watchSources will be deprecated in 1.3.0, I'm hoping that most people will migrate
          // their builds to the new system, but this will work for most builds in the interim.
          if (f.isFile && f.getParentFile != null)
            new Source(f.getParentFile, f.getName, NothingFilter, recursive = false)
          else new Source(f, AllPassFilter, NothingFilter)
        }
    }

  // Implemented with SAM conversion short-hand
  given appendFunction[A, B]: Value[A => A, A => A] = _.andThen(_)

  given appendSideEffectToFunc[A, B]: Value[A => B, () => Unit] = (f, sideEffect) => {
    f.andThen { b =>
      sideEffect()
      b
    }
  }
end Append
