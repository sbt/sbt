/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.inc

import sbt.internal.util.ConsoleLogger
import sbt.io.IO

class ZincComponentCompilerSpec extends IvyBridgeProviderSpecification {
  val scala2105 = "2.10.5"
  val scala2106 = "2.10.6"
  val scala2118 = "2.11.8"
  val scala21111 = "2.11.11"
  val scala2121 = "2.12.1"
  val scala2122 = "2.12.2"
  val scala2123 = "2.12.3"
  val scala2130 = "2.13.0"
  val scala2131 = "2.13.1"

  def isJava8: Boolean = sys.props("java.specification.version") == "1.8"

  val logger = ConsoleLogger()

  it should "compile the bridge for Scala 2.10.5 and 2.10.6" in { implicit td =>
    if (isJava8) {
      IO.withTemporaryDirectory(t => getCompilerBridge(t, logger, scala2105) should exist)
      IO.withTemporaryDirectory(t => getCompilerBridge(t, logger, scala2106) should exist)
    } else ()
  }

  it should "compile the bridge for Scala 2.11.8 and 2.11.11" in { implicit td =>
    if (isJava8) {
      IO.withTemporaryDirectory(t => getCompilerBridge(t, logger, scala2118) should exist)
      IO.withTemporaryDirectory(t => getCompilerBridge(t, logger, scala21111) should exist)
    } else ()
  }

  it should "compile the bridge for Scala 2.12.2" in { implicit td =>
    IO.withTemporaryDirectory(t => getCompilerBridge(t, logger, scala2121) should exist)
    IO.withTemporaryDirectory(t => getCompilerBridge(t, logger, scala2122) should exist)
    IO.withTemporaryDirectory(t => getCompilerBridge(t, logger, scala2123) should exist)
  }

  it should "compile the bridge for Scala 2.13" in { implicit td =>
    IO.withTemporaryDirectory(t => getCompilerBridge(t, logger, scala2130) should exist)
    IO.withTemporaryDirectory(t => getCompilerBridge(t, logger, scala2131) should exist)
  }
}
