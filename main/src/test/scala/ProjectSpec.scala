/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.io.File

object ProjectSpec extends verify.BasicTestSuite {
  object TestPlugin extends AutoPlugin {
    override def requires: Plugins = empty
  }

  private val base = new File(".")

  test("Project should normalize projectIDs if they are empty") {
    assert(Project.normalizeProjectID(emptyFilename) == Right("root"))
  }

  test("disablePlugins then enablePlugins should keep plugin enabled") {
    val p = Project("test", base).disablePlugins(TestPlugin).enablePlugins(TestPlugin)
    assert(Plugins.hasInclude(p.plugins, TestPlugin))
    assert(!Plugins.hasExclude(p.plugins, TestPlugin))
  }

  test("enablePlugins then disablePlugins should keep plugin disabled") {
    val p = Project("test", base).enablePlugins(TestPlugin).disablePlugins(TestPlugin)
    assert(!Plugins.hasInclude(p.plugins, TestPlugin))
    assert(Plugins.hasExclude(p.plugins, TestPlugin))
  }

  def emptyFilename = ""
}
