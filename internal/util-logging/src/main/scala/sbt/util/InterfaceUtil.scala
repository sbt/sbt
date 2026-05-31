/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.util

import java.io.File
import java.util.Optional
import java.util.function.Supplier
import java.util as ju

import xsbti.{
  Action,
  DiagnosticCode,
  DiagnosticRelatedInformation,
  Position,
  Problem,
  Severity,
  TextEdit,
  WorkspaceEdit,
  T2,
}

import scala.collection.mutable.ListBuffer

object InterfaceUtil {
  def toSupplier[A](a: => A): Supplier[A] = new Supplier[A] {
    override def get: A = a
  }

  import java.util.function.Function as JavaFunction
  def toJavaFunction[A1, R](f: A1 => R): JavaFunction[A1, R] =
    (t: A1) => f(t)

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

  def problem(
      cat: String,
      pos: Position,
      msg: String,
      sev: Severity,
      rendered: Option[String],
      diagnosticCode: Option[DiagnosticCode],
      diagnosticRelatedInformation: List[DiagnosticRelatedInformation],
      actions: List[Action],
  ): Problem =
    new ConcreteProblem(
      cat,
      pos,
      msg,
      sev,
      rendered,
      diagnosticCode,
      diagnosticRelatedInformation,
      actions,
    )

  def action(
      title: String,
      description: Option[String],
      edit: WorkspaceEdit,
  ): Action =
    new ConcreteAction(title, description, edit)

  def workspaceEdit(changes: List[TextEdit]): WorkspaceEdit =
    new ConcreteWorkspaceEdit(changes)

  def textEdit(position: Position, newText: String): TextEdit =
    new ConcreteTextEdit(position, newText)

  def diagnosticCode(code: String, explanation: Option[String]): DiagnosticCode =
    new ConcreteDiagnosticCode(code, explanation)

  def diagnosticRelatedInformation(
      position: Position,
      message: String
  ): DiagnosticRelatedInformation =
    new ConcreteDiagnosticRelatedInformation(position, message)

  private final class ConcreteT2[A1, A2](override val get1: A1, override val get2: A2)
      extends T2[A1, A2] {
    override def toString: String = s"ConcreteT2($get1, $get2)"
    override def equals(o: Any): Boolean = o match {
      case o: ConcreteT2[?, ?] =>
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
    override def toString: String = {
      val src = sourcePath0 match {
        case Some(x) => s"$x"
        case None    => "none"
      }
      val line = line0 match {
        case Some(x) => s":$x"
        case None    => ""
      }
      val offset = offset0 match {
        case Some(x) => s":$x"
        case None    => ""
      }
      s"""$src$line$offset"""
    }
    private def toTuple(p: Position) =
      (
        p.line,
        p.lineContent,
        p.offset,
        p.pointer,
        p.pointerSpace,
        p.sourcePath,
        p.sourceFile,
        p.startOffset,
        p.endOffset,
        p.startLine,
        p.startColumn,
        p.endLine,
        p.endColumn,
      )
    override def hashCode: Int = toTuple(this).##
    override def equals(o: Any): Boolean = o match {
      case o: Position => toTuple(this) == toTuple(o)
      case _           => false
    }
  }

  private final class ConcreteProblem(
      override val category: String,
      override val position: Position,
      override val message: String,
      override val severity: Severity,
      rendered0: Option[String],
      diagnosticCode0: Option[DiagnosticCode],
      diagnosticRelatedInformation0: List[DiagnosticRelatedInformation],
      actions0: List[Action],
  ) extends Problem {
    override val rendered = o2jo(rendered0)
    override def diagnosticCode: Optional[DiagnosticCode] = o2jo(diagnosticCode0)
    override def diagnosticRelatedInformation(): ju.List[DiagnosticRelatedInformation] =
      l2jl(diagnosticRelatedInformation0)
    @deprecated("use diagnosticRelatedInformation", "1.9.0")
    override def diagnosticRelatedInforamation(): ju.List[DiagnosticRelatedInformation] =
      diagnosticRelatedInformation()
    override def actions(): ju.List[Action] =
      l2jl(actions0)
    override def toString = s"[$severity] $position: $message"
    private def toTuple(p: Problem) =
      (
        p.category,
        p.position,
        p.message,
        p.severity,
        p.rendered,
        p.diagnosticCode,
        p.diagnosticRelatedInformation,
        p.actions,
      )
    override def hashCode: Int = toTuple(this).##
    override def equals(o: Any): Boolean = o match {
      case o: Problem => toTuple(this) == toTuple(o)
      case _          => false
    }
  }

  private final class ConcreteAction(
      override val title: String,
      description0: Option[String],
      override val edit: WorkspaceEdit,
  ) extends Action {
    override def description(): Optional[String] =
      o2jo(description0)
    override def toString(): String =
      s"Action($title, $description0, $edit)"
    private def toTuple(a: Action) =
      (
        a.title,
        a.description,
        a.edit,
      )
    override def hashCode: Int = toTuple(this).##
    override def equals(o: Any): Boolean = o match {
      case o: Action => toTuple(this) == toTuple(o)
      case _         => false
    }
  }

  private final class ConcreteWorkspaceEdit(changes0: List[TextEdit]) extends WorkspaceEdit {
    override def changes(): ju.List[TextEdit] = l2jl(changes0)
    override def toString(): String =
      s"WorkspaceEdit($changes0)"
    private def toTuple(w: WorkspaceEdit) = jl2l(w.changes)
    override def hashCode: Int = toTuple(this).##
    override def equals(o: Any): Boolean = o match {
      case o: WorkspaceEdit => toTuple(this) == toTuple(o)
      case _                => false
    }
  }

  private final class ConcreteTextEdit(
      override val position: Position,
      override val newText: String
  ) extends TextEdit {
    override def toString(): String =
      s"TextEdit($position, $newText)"
    private def toTuple(edit: TextEdit) =
      (
        edit.position,
        edit.newText,
      )
    override def hashCode: Int = toTuple(this).##
    override def equals(o: Any): Boolean = o match {
      case o: TextEdit => toTuple(this) == toTuple(o)
      case _           => false
    }
  }

  private final class ConcreteDiagnosticCode(
      override val code: String,
      explanation0: Option[String]
  ) extends DiagnosticCode {
    val explanation: Optional[String] = o2jo(explanation0)
    override def toString(): String = s"DiagnosticCode($code)"
    private def toTuple(c: DiagnosticCode) =
      (
        c.code,
        c.explanation,
      )
    override def hashCode: Int = toTuple(this).##
    override def equals(o: Any): Boolean = o match {
      case o: DiagnosticCode => toTuple(this) == toTuple(o)
      case _                 => false
    }
  }

  private final class ConcreteDiagnosticRelatedInformation(
      override val position: Position,
      override val message: String
  ) extends DiagnosticRelatedInformation {
    override def toString(): String = s"DiagnosticRelatedInformation($position, $message)"
    private def toTuple(info: DiagnosticRelatedInformation) =
      (
        info.position,
        info.message,
      )
    override def hashCode: Int = toTuple(this).##
    override def equals(o: Any): Boolean = o match {
      case o: DiagnosticRelatedInformation => toTuple(this) == toTuple(o)
      case _                               => false
    }
  }
}
