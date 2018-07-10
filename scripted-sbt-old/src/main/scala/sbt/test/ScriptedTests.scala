/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.test

import java.io.File

/**
 * This is a bincompat place holder sbt.test package that we are now trying to hide
 * because of the name conflict with Keys.test.
 */
@deprecated("Use sbt.scriptedtest.ScriptedRunner.", "1.2.0")
private[sbt] class ScriptedRunner extends sbt.scriptedtest.ScriptedRunner

/**
 * This is a bincompat place holder for sbt.test package that we are now trying to hide
 * because of the name conflict with Keys.test.
 */
@deprecated("Use sbt.scriptedtest.ScriptedTests.", "1.2.0")
private[sbt] object ScriptedTests extends ScriptedRunner {

  /** Represents the function that runs the scripted tests, both in single or batch mode. */
  type TestRunner = () => Seq[Option[String]]

  val emptyCallback: File => Unit = _ => ()
  def main(args: Array[String]): Unit =
    sbt.scriptedtest.ScriptedTests.main(args)
}
