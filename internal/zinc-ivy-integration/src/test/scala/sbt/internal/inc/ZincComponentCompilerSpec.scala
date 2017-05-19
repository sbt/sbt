package sbt.internal.inc

import sbt.internal.util.ConsoleLogger
import sbt.io.IO

class ZincComponentCompilerSpec extends BridgeProviderSpecification {
  val scala2105 = "2.10.5"
  val scala2106 = "2.10.6"
  val scala2118 = "2.11.8"
  val scala21111 = "2.11.11"
  val scala2121 = "2.12.1"
  val scala2122 = "2.12.2"

  val logger = ConsoleLogger()
  it should "compile the bridge for Scala 2.10.5 and 2.10.6" in {
    IO.withTemporaryDirectory(t => getCompilerBridge(t, logger, scala2105) should exist)
    IO.withTemporaryDirectory(t => getCompilerBridge(t, logger, scala2106) should exist)
  }

  it should "compile the bridge for Scala 2.11.8 and 2.11.11" in {
    IO.withTemporaryDirectory(t => getCompilerBridge(t, logger, scala2118) should exist)
    IO.withTemporaryDirectory(t => getCompilerBridge(t, logger, scala21111) should exist)
  }

  it should "compile the bridge for Scala 2.12.2" in {
    IO.withTemporaryDirectory(t => getCompilerBridge(t, logger, scala2121) should exist)
    IO.withTemporaryDirectory(t => getCompilerBridge(t, logger, scala2122) should exist)
  }
}
