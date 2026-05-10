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

class ZincComponentCompilerSpec extends BridgeProviderSpecification {
  val scala2107 = "2.10.7"
  val scala21112 = "2.11.12"
  val scala21221 = "2.12.21"
  val scala21311 = "2.13.11"

  val logger = ConsoleLogger()

  it should "compile the bridge for Scala 2.10.7" in { case given FixtureParam =>
    IO.withTemporaryDirectory(t => getCompilerBridge(t, logger, scala2107) should exist)
  }

  it should "compile the bridge for Scala 2.11.12" in { case given FixtureParam =>
    IO.withTemporaryDirectory(t => getCompilerBridge(t, logger, scala21112) should exist)
  }

  it should "compile the bridge for Scala 2.12.21" in { case given FixtureParam =>
    IO.withTemporaryDirectory(t => getCompilerBridge(t, logger, scala21221) should exist)
  }

  it should "compile the bridge for Scala 2.13.11" in { case given FixtureParam =>
    IO.withTemporaryDirectory(t => getCompilerBridge(t, logger, scala21311) should exist)
  }
}
