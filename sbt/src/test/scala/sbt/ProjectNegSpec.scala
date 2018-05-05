/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import org.scalatest.FunSuite
import sbt.TestUtil._

import scala.tools.reflect.ToolBoxError

class ProjectNegSpec extends FunSuite {
  def expectError(
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

  test("Fail on self-referencing project") {
    expectError("Project root cannot depend on itself") {
      """
        |import sbt._
        |import Process._
        |import Keys._
        |
        |val root = project in file(".")
        |dependsOn(root)
      """.stripMargin
    }
  }
}
