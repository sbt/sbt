package sbt.std.neg

import org.scalatest.FunSuite
import sbt.std.TaskLinterDSLFeedback
import sbt.std.TestUtil._

class TaskNegSpec extends FunSuite {
  import tools.reflect.ToolBoxError
  def expectError(errorSnippet: String,
                  compileOptions: String = "-Xfatal-warnings",
                  baseCompileOptions: String = s"-cp $toolboxClasspath")(code: String) = {
    val errorMessage = intercept[ToolBoxError] {
      eval(code, s"$compileOptions $baseCompileOptions")
    }.getMessage
    val userMessage =
      s"""
         |FOUND: $errorMessage
         |EXPECTED: $errorSnippet
      """.stripMargin
    assert(errorMessage.contains(errorSnippet), userMessage)
  }

  test("Fail on task invocation inside if it is used inside a regular task") {
    val fooNegError = TaskLinterDSLFeedback.useOfValueInsideIfExpression("fooNeg")
    val barNegError = TaskLinterDSLFeedback.useOfValueInsideIfExpression("barNeg")
    expectError(List(fooNegError, barNegError).mkString("\n")) {
      """
        |import sbt._
        |import sbt.Def._
        |
        |val fooNeg = taskKey[String]("")
        |val barNeg = taskKey[String]("")
        |var condition = true
        |
        |val bazNeg = Def.task[String] {
        |  if (condition) fooNeg.value
        |  else barNeg.value
        |}
      """.stripMargin
    }
  }

  test("Fail on task invocation inside inside if of task returned by dynamic task") {
    expectError(TaskLinterDSLFeedback.useOfValueInsideIfExpression("fooNeg")) {
      """
        |import sbt._
        |import sbt.Def._
        |
        |val fooNeg = taskKey[String]("")
        |val barNeg = taskKey[String]("")
        |var condition = true
        |
        |val bazNeg = Def.taskDyn[String] {
        |  if (condition) {
        |    Def.task {
        |      if (condition) {
        |        fooNeg.value
        |      } else ""
        |    }
        |  } else Def.task("")
        |}
      """.stripMargin
    }
  }

  test("Fail on task invocation inside inside else of task returned by dynamic task") {
    expectError(TaskLinterDSLFeedback.useOfValueInsideIfExpression("barNeg")) {
      """
        |import sbt._
        |import sbt.Def._
        |
        |val fooNeg = taskKey[String]("")
        |val barNeg = taskKey[String]("")
        |var condition = true
        |
        |val bazNeg = Def.taskDyn[String] {
        |  if (condition) {
        |    Def.task {
        |      if (condition) ""
        |      else barNeg.value
        |    }
        |  } else Def.task("")
        |}
      """.stripMargin
    }
  }
}
