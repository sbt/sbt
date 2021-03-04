/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.std

import org.scalatest.{ TestData, fixture }
import sbt.std.TestUtil._

import scala.tools.reflect.{ FrontEnd, ToolBoxError }

class TaskConfigSpec extends fixture.FunSuite with fixture.TestDataFixture {
  private def expectError(
      errorSnippet: String,
      compileOptions: String = "",
  )(code: String)(implicit td: TestData) = {
    val errorMessage = intercept[ToolBoxError] {
      eval(code, s"$compileOptions -cp ${toolboxClasspath(td)}")
      println(s"Test failed -- compilation was successful! Expected:\n$errorSnippet")
    }.getMessage
    val userMessage =
      s"""
         |FOUND: $errorMessage
         |EXPECTED: $errorSnippet
      """.stripMargin
    assert(errorMessage.contains(errorSnippet), userMessage)
  }
  private class CachingToolbox(implicit td: TestData) {
    private[this] val m = scala.reflect.runtime.currentMirror
    private[this] var _infos: List[FrontEnd#Info] = Nil
    private[this] val frontEnd = new FrontEnd {
      override def display(info: Info): Unit = _infos ::= info
      def interactive(): Unit = {}
    }

    import scala.tools.reflect.ToolBox
    val toolbox = m.mkToolBox(frontEnd, options = s"-cp ${toolboxClasspath(td)}")
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
       |  val s = 1
       |  if (condition) fooNeg.value
       |  else ""
       |}
      """.stripMargin
  private val fooNegError = TaskLinterDSLFeedback.useOfValueInsideIfExpression("fooNeg")

  test("Fail on task invocation inside if it is used inside a regular task") { implicit td =>
    val fooNegError = TaskLinterDSLFeedback.useOfValueInsideIfExpression("fooNeg")
    val code = taskDef(Some("import sbt.dsl.LinterLevel.Abort"))
    expectError(List(fooNegError).mkString("\n"))(code)
  }
  test("Succeed if the linter level is set to warn") { implicit td =>
    val toolbox = new CachingToolbox
    assert(toolbox.eval(taskDef(None)) == ((): Unit))
    assert(toolbox.infos.exists(i => i.severity.count == 1 && i.msg.contains(fooNegError)))
  }
  test("Succeed if the linter level is set to ignore") { implicit td =>
    val toolbox = new CachingToolbox
    assert(toolbox.eval(taskDef(Some("import sbt.dsl.LinterLevel.Ignore"))) == ((): Unit))
    assert(toolbox.infos.isEmpty)
  }
}
