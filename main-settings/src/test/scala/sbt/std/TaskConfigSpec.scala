/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.std

import org.scalatest.FunSuite
import sbt.std.TestUtil._

import scala.tools.reflect.{ FrontEnd, ToolBoxError }

class TaskConfigSpec extends FunSuite {
  private def expectError(
      errorSnippet: String,
      compileOptions: String = "",
      baseCompileOptions: String = s"-cp $toolboxClasspath",
  )(code: String) = {
    val errorMessage = intercept[ToolBoxError] {
      eval(code, s"$compileOptions $baseCompileOptions")
      println(s"Test failed -- compilation was successful! Expected:\n$errorSnippet")
    }.getMessage
    val userMessage =
      s"""
         |FOUND: $errorMessage
         |EXPECTED: $errorSnippet
      """.stripMargin
    assert(errorMessage.contains(errorSnippet), userMessage)
  }
  private class CachingToolbox {
    private[this] val m = scala.reflect.runtime.currentMirror
    private[this] var _infos: List[FrontEnd#Info] = Nil
    private[this] val frontEnd = new FrontEnd {
      override def display(info: Info): Unit = _infos ::= info
      override def interactive(): Unit = {}
    }

    import scala.tools.reflect.ToolBox
    val toolbox = m.mkToolBox(frontEnd, options = s"-cp $toolboxClasspath")
    def eval(code: String): Any = toolbox.eval(toolbox.parse(code))
    def infos: List[FrontEnd#Info] = _infos
  }

  private def taskDef(linterLevelImport: Option[String]): String =
    s"""
       |import sbt._
       |import sbt.Def._
       |${linterLevelImport.getOrElse("")}
       |
       |val fooNeg = taskKey[String]("")
       |var condition = true
       |
       |val barNeg = Def.task[String] {
       |  if (condition) fooNeg.value
       |  else ""
       |}
      """.stripMargin
  private val fooNegError = TaskLinterDSLFeedback.useOfValueInsideIfExpression("fooNeg")

  test("Fail on task invocation inside if it is used inside a regular task") {
    val fooNegError = TaskLinterDSLFeedback.useOfValueInsideIfExpression("fooNeg")
    expectError(List(fooNegError).mkString("\n"))(taskDef(Some("import sbt.dsl.LinterLevel.Abort")))
  }
  test("Succeed if the linter level is set to warn") {
    val toolbox = new CachingToolbox
    assert(toolbox.eval(taskDef(None)) == ((): Unit))
    assert(toolbox.infos.exists(i => i.severity.count == 1 && i.msg.contains(fooNegError)))
  }
  test("Succeed if the linter level is set to ignore") {
    val toolbox = new CachingToolbox
    assert(toolbox.eval(taskDef(Some("import sbt.dsl.LinterLevel.Ignore"))) == ((): Unit))
    assert(toolbox.infos.isEmpty)
  }
}
