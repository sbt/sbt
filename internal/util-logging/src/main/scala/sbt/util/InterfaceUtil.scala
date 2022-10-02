/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.util

import java.io.File
import java.util.Optional
import java.util.function.Supplier
import java.{ util => ju }

import xsbti.{ DiagnosticCode, DiagnosticRelatedInformation, Position, Problem, Severity, T2 }

import scala.collection.mutable.ListBuffer

object InterfaceUtil {
  def toSupplier[A](a: => A): Supplier[A] = new Supplier[A] {
    override def get: A = a
  }

  import java.util.function.{ Function => JavaFunction }
  def toJavaFunction[A1, R](f: A1 => R): JavaFunction[A1, R] = new JavaFunction[A1, R] {
    override def apply(t: A1): R = f(t)
  }

  def t2[A1, A2](x: (A1, A2)): T2[A1, A2] = new ConcreteT2(x._1, x._2)

  def toOption[A](m: Optional[A]): Option[A] =
    if (m.isPresent) Some(m.get) else None

  def toOptional[A](o: Option[A]): Optional[A] =
    o match {
      case Some(v) => Optional.of(v)
      case None    => Optional.empty()
    }

  def jo2o[A](o: Optional[A]): Option[A] =
    if (o.isPresent) Some(o.get)
    else None

  def o2jo[A](o: Option[A]): Optional[A] =
    o match {
      case Some(v) => Optional.ofNullable(v)
      case None    => Optional.empty[A]()
    }

  def l2jl[A](l: List[A]): ju.List[A] = {
    val jl = new ju.ArrayList[A](l.size)
    l.foreach(jl.add(_))
    jl
  }

  def jl2l[A](jl: ju.List[A]): List[A] = {
    val l = ListBuffer[A]()
    jl.forEach(l += _)
    l.toList
  }

  @deprecated("Use the overload of this method with more arguments", "1.2.2")
  def position(
      line0: Option[Integer],
      content: String,
      offset0: Option[Integer],
      pointer0: Option[Integer],
      pointerSpace0: Option[String],
      sourcePath0: Option[String],
      sourceFile0: Option[File]
  ): Position =
    position(
      line0,
      content,
      offset0,
      pointer0,
      pointerSpace0,
      sourcePath0,
      sourceFile0,
      None,
      None,
      None,
      None,
      None,
      None
    )

  def position(
      line0: Option[Integer],
      content: String,
      offset0: Option[Integer],
      pointer0: Option[Integer],
      pointerSpace0: Option[String],
      sourcePath0: Option[String],
      sourceFile0: Option[File],
      startOffset0: Option[Integer],
      endOffset0: Option[Integer],
      startLine0: Option[Integer],
      startColumn0: Option[Integer],
      endLine0: Option[Integer],
      endColumn0: Option[Integer]
  ): Position =
    new ConcretePosition(
      line0,
      content,
      offset0,
      pointer0,
      pointerSpace0,
      sourcePath0,
      sourceFile0,
      startOffset0,
      endOffset0,
      startLine0,
      startColumn0,
      endLine0,
      endColumn0
    )

  @deprecated("Use the overload of this method with more arguments", "1.2.2")
  def problem(cat: String, pos: Position, msg: String, sev: Severity): Problem =
    problem(cat, pos, msg, sev, None)

  @deprecated("Use the overload of this method with more arguments", "1.7.2")
  def problem(
      cat: String,
      pos: Position,
      msg: String,
      sev: Severity,
      rendered: Option[String]
  ): Problem =
    problem(cat, pos, msg, sev, rendered, None, List.empty[DiagnosticRelatedInformation])

  def problem(
      cat: String,
      pos: Position,
      msg: String,
      sev: Severity,
      rendered: Option[String],
      diagnosticCode: Option[DiagnosticCode],
      diagnosticRelatedInforamation: List[DiagnosticRelatedInformation]
  ): Problem =
    new ConcreteProblem(cat, pos, msg, sev, rendered, diagnosticCode, diagnosticRelatedInforamation)

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
    override def hashCode: Int = {
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
      sourceFile0: Option[File],
      startOffset0: Option[Integer],
      endOffset0: Option[Integer],
      startLine0: Option[Integer],
      startColumn0: Option[Integer],
      endLine0: Option[Integer],
      endColumn0: Option[Integer]
  ) extends Position {
    val line = o2jo(line0)
    val lineContent = content
    val offset = o2jo(offset0)
    val pointer = o2jo(pointer0)
    val pointerSpace = o2jo(pointerSpace0)
    val sourcePath = o2jo(sourcePath0)
    val sourceFile = o2jo(sourceFile0)
    override val startOffset = o2jo(startOffset0)
    override val endOffset = o2jo(endOffset0)
    override val startLine = o2jo(startLine0)
    override val startColumn = o2jo(startColumn0)
    override val endLine = o2jo(endLine0)
    override val endColumn = o2jo(endColumn0)
  }

  private final class ConcreteProblem(
      cat: String,
      pos: Position,
      msg: String,
      sev: Severity,
      rendered0: Option[String],
      diagnosticCode0: Option[DiagnosticCode],
      diagnosticRelatedInformation0: List[DiagnosticRelatedInformation]
  ) extends Problem {
    val category = cat
    val position = pos
    val message = msg
    val severity = sev
    override val rendered = o2jo(rendered0)
    override def diagnosticCode: Optional[DiagnosticCode] = o2jo(diagnosticCode0)
    override def diagnosticRelatedInforamation(): ju.List[DiagnosticRelatedInformation] =
      l2jl(diagnosticRelatedInformation0)
    override def toString = s"[$severity] $pos: $message"
  }
}
