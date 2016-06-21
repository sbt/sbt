package sbt

import java.io.File
import Def.Classpath
import scala.annotation.implicitNotFound
import sbt.internal.util.Attributed

object Append {
  @implicitNotFound(msg = "No implicit for Append.Value[${A}, ${B}] found,\n  so ${B} cannot be appended to ${A}")
  trait Value[A, B] {
    def appendValue(a: A, b: B): A
  }
  @implicitNotFound(msg = "No implicit for Append.Values[${A}, ${B}] found,\n  so ${B} cannot be appended to ${A}")
  trait Values[A, -B] {
    def appendValues(a: A, b: B): A
  }
  trait Sequence[A, -B, T] extends Value[A, T] with Values[A, B]

  implicit def appendSeq[T, V <: T]: Sequence[Seq[T], Seq[V], V] = new Sequence[Seq[T], Seq[V], V] {
    def appendValues(a: Seq[T], b: Seq[V]): Seq[T] = a ++ b
    def appendValue(a: Seq[T], b: V): Seq[T] = a :+ b
  }
  implicit def appendSeqImplicit[T, V](implicit ev: V => T): Sequence[Seq[T], Seq[V], V] = new Sequence[Seq[T], Seq[V], V] {
    def appendValues(a: Seq[T], b: Seq[V]): Seq[T] = a ++ (b map { x => (x: T) })
    def appendValue(a: Seq[T], b: V): Seq[T] = a :+ (b: T)
  }
  implicit def appendList[T, V <: T]: Sequence[List[T], List[V], V] = new Sequence[List[T], List[V], V] {
    def appendValues(a: List[T], b: List[V]): List[T] = a ::: b
    def appendValue(a: List[T], b: V): List[T] = a :+ b
  }
  implicit def appendListImplicit[T, V](implicit ev: V => T): Sequence[List[T], List[V], V] = new Sequence[List[T], List[V], V] {
    def appendValues(a: List[T], b: List[V]): List[T] = a ::: (b map { x => (x: T) })
    def appendValue(a: List[T], b: V): List[T] = a :+ (b: T)
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
  implicit def appendClasspath: Sequence[Classpath, Seq[File], File] = new Sequence[Classpath, Seq[File], File] {
    def appendValues(a: Classpath, b: Seq[File]): Classpath = a ++ Attributed.blankSeq(b)
    def appendValue(a: Classpath, b: File): Classpath = a :+ Attributed.blank(b)
  }
  implicit def appendSet[T, V <: T]: Sequence[Set[T], Set[V], V] = new Sequence[Set[T], Set[V], V] {
    def appendValues(a: Set[T], b: Set[V]): Set[T] = a ++ b
    def appendValue(a: Set[T], b: V): Set[T] = a + b
  }
  implicit def appendMap[A, B, X <: A, Y <: B]: Sequence[Map[A, B], Map[X, Y], (X, Y)] = new Sequence[Map[A, B], Map[X, Y], (X, Y)] {
    def appendValues(a: Map[A, B], b: Map[X, Y]): Map[A, B] = a ++ b
    def appendValue(a: Map[A, B], b: (X, Y)): Map[A, B] = a + b
  }
  implicit def appendOption[T]: Sequence[Seq[T], Option[T], Option[T]] =
    new Sequence[Seq[T], Option[T], Option[T]] {
      def appendValue(a: Seq[T], b: Option[T]): Seq[T] = b.fold(a)(a :+ _)
      def appendValues(a: Seq[T], b: Option[T]): Seq[T] = b.fold(a)(a :+ _)
    }
}
