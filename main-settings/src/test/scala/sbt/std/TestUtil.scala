/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.std

import org.scalatest.TestData

import scala.tools.reflect.ToolBox

object TestUtil {
  def eval(code: String, compileOptions: String = ""): Any = {
    val tb = mkToolbox(compileOptions)
    tb.eval(tb.parse(code))
  }

  def mkToolbox(compileOptions: String = ""): ToolBox[_ <: scala.reflect.api.Universe] = {
    val m = scala.reflect.runtime.currentMirror
    import scala.tools.reflect.ToolBox
    m.mkToolBox(options = compileOptions)
  }

  def toolboxClasspath(td: TestData): String =
    td.configMap.get("sbt.server.classpath") match {
      case Some(s: String) => s
      case _               => throw new IllegalStateException("No classpath specified.")
    }
}
