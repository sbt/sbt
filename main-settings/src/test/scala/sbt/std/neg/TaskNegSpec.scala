/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.std.neg

import org.scalatest.FunSuite
import sbt.std.TaskLinterDSLFeedback
import sbt.std.TestUtil._

class TaskNegSpec extends FunSuite {
  import tools.reflect.ToolBoxError
  def expectError(errorSnippet: String,
                  compileOptions: String = "",
                  baseCompileOptions: String = s"-cp $toolboxClasspath")(code: String) = {
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

  test("Fail on task invocation inside `if` if it is used inside a regular task") {
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
        |def bi(s: String) = s + "  "
        |
        |val bazNeg = Def.task[String] {
        |  if (condition) "" + fooNeg.value
        |  else bi(barNeg.value)
        |}
      """.stripMargin
    }
  }

  test("Fail on task invocation inside `if` of task returned by dynamic task") {
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

  test("Fail on task invocation inside nested `if` of task returned by dynamic task") {
    val fooNegCatch = TaskLinterDSLFeedback.useOfValueInsideIfExpression("fooNeg")
    val barNegCatch = TaskLinterDSLFeedback.useOfValueInsideIfExpression("barNeg")
    expectError(List(fooNegCatch, barNegCatch).mkString("\n")) {
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
        |        val first = if (!condition && condition) {
        |          fooNeg.value
        |        } else ""
        |        if ("true".toBoolean) first
        |        else {
        |          barNeg.value
        |        }
        |      } else ""
        |    }
        |  } else Def.task("")
        |}
      """.stripMargin
    }
  }

  test("Fail on task invocation inside else of task returned by dynamic task") {
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

  test("Fail on task invocation inside anonymous function returned by regular task") {
    val fooNegError = TaskLinterDSLFeedback.useOfValueInsideAnon("fooNeg")
    expectError(fooNegError) {
      """
        |import sbt._
        |import sbt.Def._
        |
        |val fooNeg = taskKey[String]("")
        |val barNeg = taskKey[String]("")
        |var condition = true
        |
        |val bazNeg = Def.task[String] {
        |  val anon = () => fooNeg.value
        |  if (condition) anon()
        |  else anon()
        |}
      """.stripMargin
    }
  }

  test("Fail on task invocation inside nested anonymous function returned by regular task") {
    val fooNegError = TaskLinterDSLFeedback.useOfValueInsideAnon("fooNeg")
    val barNegError = TaskLinterDSLFeedback.useOfValueInsideAnon("barNeg")
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
        |  val anon = () => { val _ = () => fooNeg.value; barNeg.value}
        |  if (condition) anon()
        |  else anon()
        |}
      """.stripMargin
    }
  }

  test("Fail on task invocation inside complex anonymous function returned by regular task") {
    val fooNegError = TaskLinterDSLFeedback.useOfValueInsideAnon("fooNeg")
    expectError(fooNegError) {
      """
        |import sbt._
        |import sbt.Def._
        |
        |val fooNeg = taskKey[String]("")
        |var condition = true
        |
        |val bazNeg = Def.task[String] {
        |  val anon = () => fooNeg.value + ""
        |  if (condition) anon()
        |  else anon()
        |}
      """.stripMargin
    }
  }

  test("Fail on task invocation inside anonymous function returned by dynamic task") {
    val fooNegError = TaskLinterDSLFeedback.useOfValueInsideAnon("fooNeg")
    expectError(fooNegError) {
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
        |    val anon = () => fooNeg.value
        |    Def.task(anon())
        |  } else Def.task("")
        |}
      """.stripMargin
    }
  }

  test("Detect a missing `.value` inside a task") {
    expectError(TaskLinterDSLFeedback.missingValueForKey("fooNeg")) {
      """
        |import sbt._
        |import sbt.Def._
        |
        |val fooNeg = taskKey[String]("")
        |
        |def avoidDCE = {println(""); ""}
        |val bazNeg = Def.task[String] {
        |  fooNeg
        |  avoidDCE
        |}
      """.stripMargin
    }
  }

  test("Detect a missing `.value` inside a val definition of a task") {
    expectError(TaskLinterDSLFeedback.missingValueForKey("fooNeg2")) {
      """
        |import sbt._
        |import sbt.Def._
        |
        |val fooNeg2 = taskKey[String]("")
        |
        |def avoidDCE = {println(""); ""}
        |val bazNeg = Def.task[String] {
        |  val _ = fooNeg2
        |  avoidDCE
        |}
      """.stripMargin
    }
  }

  test("Detect a missing `.value` inside a val definition of an inner method of a task") {
    expectError(TaskLinterDSLFeedback.missingValueForKey("fooNeg2")) {
      """
        |import sbt._
        |import sbt.Def._
        |
        |val fooNeg2 = taskKey[String]("")
        |
        |def avoidDCE = {println(""); ""}
        |val bazNeg = Def.task[String] {
        |  def inner = {
        |    val _ = fooNeg2
        |    avoidDCE
        |  }
        |  inner
        |}
      """.stripMargin
    }
  }

  test("Detect a missing `.value` inside an inner method of a task") {
    expectError(TaskLinterDSLFeedback.missingValueForKey("fooNeg3")) {
      """
        |import sbt._
        |import sbt.Def._
        |
        |val fooNeg3 = taskKey[String]("")
        |def avoidDCE = {println(""); ""}
        |val bazNeg = Def.task[String] {
        |  def inner: String = {
        |    fooNeg3
        |    avoidDCE
        |  }
        |  inner
        |}
      """.stripMargin
    }
  }

  test("Detect a missing `.value` inside a task whose return type is Unit") {
    expectError(TaskLinterDSLFeedback.missingValueForKey("fooNeg4")) {
      """
        |import sbt._
        |import sbt.Def._
        |
        |val fooNeg4 = taskKey[String]("")
        |
        |val bazNeg = Def.task[Unit] {
        |  fooNeg4
        |}
      """.stripMargin
    }
  }

  // Enable these tests when https://github.com/scala/bug/issues/10340 is fixed

  /*
  test("Detect a missing `.value` inside a val of an inner method of a task returning a literal") {
    expectError(TaskLinterDSLFeedback.missingValueForKey("fooNeg3")) {
      """
        |import sbt._
        |import sbt.Def._
        |
        |val fooNeg3 = taskKey[String]("")
        |
        |val bazNeg = Def.task[String] {
        |  def inner: String = {
        |    val _ = fooNeg3
        |    ""
        |  }
        |  inner
        |}
      """.stripMargin
    }
  }

  test("Detect a missing `.value` inside a val of a task returning a literal") {
    expectError(TaskLinterDSLFeedback.missingValueForKey("fooNeg3")) {
      """
        |import sbt._
        |import sbt.Def._
        |
        |val fooNeg3 = taskKey[String]("")
        |
        |val bazNeg = Def.task[String] {
        |  val _ = fooNeg3
        |  ""
        |}
      """.stripMargin
    }
  }
 */
}
