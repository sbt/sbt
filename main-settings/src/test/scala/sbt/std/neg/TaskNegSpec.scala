package sbt.std.neg

import org.scalatest.FunSuite
import sbt.std.TestUtil._

class TaskNegSpec extends FunSuite {
  import tools.reflect.ToolBoxError
  def expectError(errorSnippet: String,
                  compileOptions: String = "-Xmacro-settings:debug-spores",
                  baseCompileOptions: String = s"-cp $toolboxClasspath")(code: String) = {
    val errorMessage = intercept[ToolBoxError] {
      eval(code, s"$compileOptions $baseCompileOptions")
      println("ERROR: The snippet compiled successfully.")
    }.getMessage
    val userMessage =
      s"""
         |FOUND: $errorMessage
         |EXPECTED: $errorSnippet
      """.stripMargin
    println(userMessage)
    assert(errorMessage.contains(errorSnippet), userMessage)
  }

  test("Fail on task invocation inside if of regular task") {

    expectError("DSL error: a task value cannot be obtained inside if.") {
      """
        |import sbt._
        |import sbt.Def._
        |
        |val fooNeg = taskKey[String]("")
        |val barNeg = taskKey[String]("")
        |var condition = true
        |
        |val bazNeg: Initialize[Task[String]] = Def.task[String] {
        |  if (condition) fooNeg.value
        |  else barNeg.value
        |}
      """.stripMargin
    }
  }
}
