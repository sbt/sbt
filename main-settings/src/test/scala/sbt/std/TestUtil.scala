/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.std

import scala.reflect._

object TestUtil {
  import tools.reflect.ToolBox

  def eval(code: String, compileOptions: String = ""): Any = {
    val tb = mkToolbox(compileOptions)
    tb.eval(tb.parse(code))
  }

  def mkToolbox(compileOptions: String = ""): ToolBox[_ <: scala.reflect.api.Universe] = {
    val m = scala.reflect.runtime.currentMirror
    import scala.tools.reflect.ToolBox
    m.mkToolBox(options = compileOptions)
  }

  lazy val toolboxClasspath: String = {
    val mainClassesDir = buildinfo.TestBuildInfo.classDirectory
    val testClassesDir = buildinfo.TestBuildInfo.test_classDirectory
    val depsClasspath = buildinfo.TestBuildInfo.dependencyClasspath
    mainClassesDir +: testClassesDir +: depsClasspath mkString java.io.File.pathSeparator
  }
}
