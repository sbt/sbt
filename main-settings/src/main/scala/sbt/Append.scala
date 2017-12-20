/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import java.io.File
import Def.Classpath
import scala.annotation.implicitNotFound
import sbt.internal.util.Attributed
import Def.Initialize
import reflect.internal.annotations.compileTimeOnly
import sbt.internal.io.Source
import sbt.io.{ AllPassFilter, NothingFilter }

object Append {
  @implicitNotFound(
    msg = "No implicit for Append.Value[${A}, ${B}] found,\n  so ${B} cannot be appended to ${A}")
  trait Value[A, B] {
    def appendValue(a: A, b: B): A
  }
  @implicitNotFound(
    msg = "No implicit for Append.Values[${A}, ${B}] found,\n  so ${B} cannot be appended to ${A}")
  trait Values[A, -B] {
    def appendValues(a: A, b: B): A
  }
  trait Sequence[A, -B, T] extends Value[A, T] with Values[A, B]

  implicit def appendSeq[T, V <: T]: Sequence[Seq[T], Seq[V], V] =
    new Sequence[Seq[T], Seq[V], V] {
      def appendValues(a: Seq[T], b: Seq[V]): Seq[T] = a ++ b
      def appendValue(a: Seq[T], b: V): Seq[T] = a :+ b
    }
  implicit def appendSeqImplicit[T, V](implicit ev: V => T): Sequence[Seq[T], Seq[V], V] =
    new Sequence[Seq[T], Seq[V], V] {
      def appendValues(a: Seq[T], b: Seq[V]): Seq[T] =
        a ++ (b map { x =>
          (x: T)
        })
      def appendValue(a: Seq[T], b: V): Seq[T] = a :+ (b: T)
    }
  @compileTimeOnly("This can be used in += only.")
  implicit def appendTaskValueSeq[T, V <: T]: Value[Seq[Task[T]], Initialize[Task[V]]] =
    new Value[Seq[Task[T]], Initialize[Task[V]]] {
      def appendValue(a: Seq[Task[T]], b: Initialize[Task[V]]): Seq[Task[T]] = ???
    }
  @compileTimeOnly("This can be used in += only.")
  implicit def appendTaskKeySeq[T, V <: T]: Value[Seq[Task[T]], TaskKey[V]] =
    new Value[Seq[Task[T]], TaskKey[V]] {
      def appendValue(a: Seq[Task[T]], b: TaskKey[V]): Seq[Task[T]] = ???
    }
  implicit def appendList[T, V <: T]: Sequence[List[T], List[V], V] =
    new Sequence[List[T], List[V], V] {
      def appendValues(a: List[T], b: List[V]): List[T] = a ::: b
      def appendValue(a: List[T], b: V): List[T] = a :+ b
    }
  implicit def appendListImplicit[T, V](implicit ev: V => T): Sequence[List[T], List[V], V] =
    new Sequence[List[T], List[V], V] {
      def appendValues(a: List[T], b: List[V]): List[T] =
        a ::: (b map { x =>
          (x: T)
        })
      def appendValue(a: List[T], b: V): List[T] = a :+ (b: T)
    }
  implicit def appendVectorImplicit[T, V](implicit ev: V => T): Sequence[Vector[T], Seq[V], V] =
    new Sequence[Vector[T], Seq[V], V] {
      def appendValues(a: Vector[T], b: Seq[V]): Vector[T] =
        a ++ (b map { x =>
          (x: T)
        })
      def appendValue(a: Vector[T], b: V): Vector[T] = a :+ (b: T)
    }
  implicit def appendString: Value[String, String] = new Value[String, String] {
    def appendValue(a: String, b: String) = a + b
  }
  implicit def appendInt = new Value[Int, Int] {
    def appendValue(a: Int, b: Int) = a + b
  }
  implicit def appendLong = new Value[Long, Long] {
    def appendValue(a: Long, b: Long) = a + b
  }
  implicit def appendDouble = new Value[Double, Double] {
    def appendValue(a: Double, b: Double) = a + b
  }
  implicit def appendClasspath: Sequence[Classpath, Seq[File], File] =
    new Sequence[Classpath, Seq[File], File] {
      def appendValues(a: Classpath, b: Seq[File]): Classpath = a ++ Attributed.blankSeq(b)
      def appendValue(a: Classpath, b: File): Classpath = a :+ Attributed.blank(b)
    }
  implicit def appendSet[T, V <: T]: Sequence[Set[T], Set[V], V] =
    new Sequence[Set[T], Set[V], V] {
      def appendValues(a: Set[T], b: Set[V]): Set[T] = a ++ b
      def appendValue(a: Set[T], b: V): Set[T] = a + b
    }
  implicit def appendMap[A, B, X <: A, Y <: B]: Sequence[Map[A, B], Map[X, Y], (X, Y)] =
    new Sequence[Map[A, B], Map[X, Y], (X, Y)] {
      def appendValues(a: Map[A, B], b: Map[X, Y]): Map[A, B] = a ++ b
      def appendValue(a: Map[A, B], b: (X, Y)): Map[A, B] = a + b
    }
  implicit def appendOption[T]: Sequence[Seq[T], Option[T], Option[T]] =
    new Sequence[Seq[T], Option[T], Option[T]] {
      def appendValue(a: Seq[T], b: Option[T]): Seq[T] = b.fold(a)(a :+ _)
      def appendValues(a: Seq[T], b: Option[T]): Seq[T] = b.fold(a)(a :+ _)
    }
  implicit def appendSource: Sequence[Seq[Source], Seq[File], File] =
    new Sequence[Seq[Source], Seq[File], File] {
      def appendValue(a: Seq[Source], b: File): Seq[Source] =
        appendValues(a, Seq(b))
      def appendValues(a: Seq[Source], b: Seq[File]): Seq[Source] =
        a ++ b.map(new Source(_, AllPassFilter, NothingFilter))
    }
}
