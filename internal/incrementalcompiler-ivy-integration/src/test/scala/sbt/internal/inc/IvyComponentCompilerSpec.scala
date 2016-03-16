package sbt.internal.inc

import sbt.io.IO
import sbt.util.Logger

class IvyComponentCompilerSpec extends BridgeProviderSpecification {

  val scala210 = "2.10.5"
  val scala211 = "2.11.7"

  it should "compile the bridge for Scala 2.10" in {
    IO.withTemporaryDirectory { tempDir =>
      getCompilerBridge(tempDir, Logger.Null, scala210) should exist
    }
  }

  it should "compile the bridge for Scala 2.11" in {
    IO.withTemporaryDirectory { tempDir =>
      getCompilerBridge(tempDir, Logger.Null, scala211) should exist
    }
  }

}
