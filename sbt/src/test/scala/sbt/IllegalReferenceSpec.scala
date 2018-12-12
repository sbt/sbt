/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import org.scalatest.FunSuite

import scala.tools.reflect.{ FrontEnd, ToolBoxError }

class IllegalReferenceSpec extends FunSuite {
  lazy val toolboxClasspath: String = {
    val mainClassesDir = buildinfo.TestBuildInfo.classDirectory
    val testClassesDir = buildinfo.TestBuildInfo.test_classDirectory
    val depsClasspath = buildinfo.TestBuildInfo.dependencyClasspath
    mainClassesDir +: testClassesDir +: depsClasspath mkString java.io.File.pathSeparator
  }
  def eval(code: String, compileOptions: String = ""): Any = {
    val m = scala.reflect.runtime.currentMirror
    import scala.tools.reflect.ToolBox
    val tb = m.mkToolBox(options = compileOptions)
    tb.eval(tb.parse(code))
  }
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

  test("Def.sequential should be legal within Def.taskDyn") {
    val toolbox = new CachingToolbox
    // This example was taken from @dos65 in https://github.com/sbt/sbt/issues/3110
    val build =
      s"""
         |import sbt._
         |import Keys._
         |
         |Def.taskDyn[Int] {
         |  // Calling baseDirectory.value will cause the task macros to add a reference to
         |  // `Def.toITask(sbt.Keys.baseDirectory)`. This, in turn, causes `Def` to be added
         |  // to a list of local definitions. Later on, we dereference `Def` with
         |  // `Def.sequential` which used to erroneously cause an illegal dynamic reference.
         |  baseDirectory.value
         |  Def.sequential(Def.task(42))
         |}.dependencies.headOption.map(_.key.label)
       """.stripMargin
    assert(toolbox.eval(build) == Some("baseDirectory"))
    assert(toolbox.infos.isEmpty)
  }
  test("Local task defs should be illegal within Def.task") {
    val build =
      s"""
         |import sbt._
         |import Keys._
         |
         |Def.task[Int] {
         |  def foo = Def.task(5)
         |  foo.value
         |}.dependencies.headOption.map(_.key.label)
       """.stripMargin
    expectError("Illegal dynamic reference: foo")(build)
  }
}
