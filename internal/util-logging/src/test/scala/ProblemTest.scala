/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.net.URI
import hedgehog.*
import hedgehog.runner.*
import _root_.sbt.util.InterfaceUtil
import InterfaceUtil.{ jl2l, jo2o, l2jl }
import xsbti.*

object ProblemTest extends Properties {
  override def tests: List[Test] = List(
    property(
      "All problems can toString",
      genProblem.forAll.map(toStringCheck),
    ),
    property(
      "All problems can be compared structurally",
      genProblem.forAll.map(equalityCheck),
    ),
    property(
      "All diagnostic codes can be compared structurally",
      genDiagnosticCode.forAll.map(equalityCheck),
    ),
    property(
      "All diagnostic related information can be compared structurally",
      genDiagnosticRelatedInformation.forAll.map(equalityCheck),
    ),
    property(
      "All actions can be compared structurally",
      genAction.forAll.map(equalityCheck),
    ),
  )

  def toStringCheck(p: Problem): Result =
    Result.assert(p.toString() != "")

  def equalityCheck(p: Problem): Result = {
    val other = InterfaceUtil.problem(
      p.category,
      p.position,
      p.message,
      p.severity,
      jo2o(p.rendered),
      jo2o(p.diagnosticCode).map(copy),
      jl2l(p.diagnosticRelatedInformation).map(copy),
      jl2l(p.actions).map(copy),
    )
    Result
      .assert(p == other)
      .log(s"$p == $other")
  }

  def equalityCheck(c: DiagnosticCode): Result = {
    val other = copy(c)
    Result.assert(c == other)
  }

  def equalityCheck(info: DiagnosticRelatedInformation): Result = {
    val other = copy(info)
    Result.assert(info == other)
  }

  def equalityCheck(a: Action): Result = {
    val other = copy(a)
    Result.assert(a == other)
  }

  lazy val genProblem: Gen[Problem] =
    for {
      cat <- genString
      pos <- genPosition
      msg <- genString
      sev <- genSeverity
      rendered <- optString
      code <- optDiagnosticCode
      info <- listDiagnosticRelatedInformation
      actions <- listAction
    } yield InterfaceUtil.problem(
      cat,
      pos,
      msg,
      sev,
      rendered,
      code,
      info,
      actions,
    )

  lazy val optDiagnosticCode: Gen[Option[DiagnosticCode]] =
    Gen.choice1(genDiagnosticCode.map(Some(_)), Gen.constant(None))

  lazy val genDiagnosticCode: Gen[DiagnosticCode] =
    for {
      code <- Gen.int(Range.linear(0, 1024))
    } yield InterfaceUtil.diagnosticCode("E" + code.toString, None)

  lazy val genSeverity: Gen[Severity] =
    Gen.element(Severity.Info, List(Severity.Warn, Severity.Error))

  lazy val genPosition: Gen[Position] =
    for {
      line <- optIntGen
      content <- genString
      offset <- optIntGen
    } yield InterfaceUtil.position(
      line,
      content,
      offset,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
    )

  lazy val listDiagnosticRelatedInformation: Gen[List[DiagnosticRelatedInformation]] =
    Gen.list(genDiagnosticRelatedInformation, Range.linear(0, 2))

  lazy val genDiagnosticRelatedInformation: Gen[DiagnosticRelatedInformation] =
    for {
      pos <- genPosition
      message <- genString
    } yield InterfaceUtil.diagnosticRelatedInformation(pos, message)

  lazy val listAction: Gen[List[Action]] =
    Gen.list(genAction, Range.linear(0, 2))

  lazy val genAction: Gen[Action] =
    for {
      title <- genString
      description <- optString
      edit <- genWorkspaceEdit
    } yield InterfaceUtil.action(title, description, edit)

  lazy val genWorkspaceEdit: Gen[WorkspaceEdit] =
    for {
      changes <- listTextEdit
    } yield InterfaceUtil.workspaceEdit(changes)

  lazy val listTextEdit: Gen[List[TextEdit]] =
    Gen.list(genTextEdit, Range.linear(0, 2))

  lazy val genTextEdit: Gen[TextEdit] =
    for {
      pos <- genPosition
      newText <- genString
    } yield InterfaceUtil.textEdit(pos, newText)

  lazy val genUri: Gen[URI] =
    for {
      ssp <- genString
    } yield new URI("file", "///" + ssp, null)

  lazy val optString: Gen[Option[String]] =
    Gen.choice1(genString.map(Some(_)), Gen.constant(None))

  lazy val genString = Gen.string(Gen.alphaNum, Range.linear(0, 256))

  lazy val optIntGen: Gen[Option[Integer]] =
    Gen.choice1(Gen.int(Range.linear(0, 1024)).map(Some(_)), Gen.constant(None))

  private def copy(c: DiagnosticCode): DiagnosticCode =
    new DiagnosticCode() {
      val code = c.code
      override def explanation = c.explanation
    }

  private def copy(info: DiagnosticRelatedInformation): DiagnosticRelatedInformation =
    new DiagnosticRelatedInformation() {
      override def position = info.position
      override def message = info.message
    }

  private def copy(a: Action): Action =
    new Action {
      override def title = a.title
      override def description = a.description
      override def edit = copy(a.edit)
    }

  private def copy(edit: WorkspaceEdit): WorkspaceEdit =
    new WorkspaceEdit {
      override def changes() =
        l2jl(jl2l(edit.changes).map(copy))
    }

  private def copy(edit: TextEdit): TextEdit =
    new TextEdit {
      override val position = edit.position
      override val newText = edit.newText
    }
}
