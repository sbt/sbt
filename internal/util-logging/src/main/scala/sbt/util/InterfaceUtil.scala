package sbt.util

import xsbti.{ Maybe, F0, F1, T2, Position, Problem, Severity }
import java.io.File
import java.util.Optional

object InterfaceUtil {
  def f0[A](a: => A): F0[A] = new ConcreteF0[A](a)
  def f1[A1, R](f: A1 => R): F1[A1, R] = new ConcreteF1(f)
  def t2[A1, A2](x: (A1, A2)): T2[A1, A2] = new ConcreteT2(x._1, x._2)

  def m2o[A](m: Maybe[A]): Option[A] =
    if (m.isDefined) Some(m.get)
    else None

  def o2m[A](o: Option[A]): Maybe[A] =
    o match {
      case Some(v) => Maybe.just(v)
      case None    => Maybe.nothing()
    }

  def jo2o[A](o: Optional[A]): Option[A] =
    if (o.isPresent) Some(o.get)
    else None

  def o2jo[A](o: Option[A]): Optional[A] =
    o match {
      case Some(v) => Optional.ofNullable(v)
      case None    => Optional.empty[A]()
    }

  def position(line0: Option[Integer], content: String, offset0: Option[Integer], pointer0: Option[Integer],
    pointerSpace0: Option[String], sourcePath0: Option[String], sourceFile0: Option[File]): Position =
    new ConcretePosition(line0, content, offset0, pointer0, pointerSpace0, sourcePath0, sourceFile0)

  def problem(cat: String, pos: Position, msg: String, sev: Severity): Problem =
    new ConcreteProblem(cat, pos, msg, sev)

  private final class ConcreteF0[A](a: => A) extends F0[A] {
    def apply: A = a
  }

  private final class ConcreteF1[A1, R](f: A1 => R) extends F1[A1, R] {
    def apply(a1: A1): R = f(a1)
  }

  private final class ConcreteT2[A1, A2](a1: A1, a2: A2) extends T2[A1, A2] {
    val get1: A1 = a1
    val get2: A2 = a2
    override def toString: String = s"ConcreteT2($a1, $a2)"
    override def equals(o: Any): Boolean = o match {
      case o: ConcreteT2[A1, A2] =>
        this.get1 == o.get1 &&
          this.get2 == o.get2
      case _ => false
    }
    override def hashCode: Int =
      {
        var hash = 1
        hash = hash * 31 + this.get1.##
        hash = hash * 31 + this.get2.##
        hash
      }
  }

  private final class ConcretePosition(
    line0: Option[Integer],
    content: String,
    offset0: Option[Integer],
    pointer0: Option[Integer],
    pointerSpace0: Option[String],
    sourcePath0: Option[String],
    sourceFile0: Option[File]
  ) extends Position {
    val line = o2jo(line0)
    val lineContent = content
    val offset = o2jo(offset0)
    val pointer = o2jo(pointer0)
    val pointerSpace = o2jo(pointerSpace0)
    val sourcePath = o2jo(sourcePath0)
    val sourceFile = o2jo(sourceFile0)
  }

  private final class ConcreteProblem(
    cat: String,
    pos: Position,
    msg: String,
    sev: Severity
  ) extends Problem {
    val category = cat
    val position = pos
    val message = msg
    val severity = sev
    override def toString = s"[$severity] $pos: $message"
  }
}
