/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.server

import sbt.protocol.Serialization
import verify.BasicTestSuite

object NetworkChannelSpec extends BasicTestSuite:

  test("only systemOut and systemErr are dropped while canceling") {
    assert(NetworkChannel.isCanceledOutput(Serialization.systemOut))
    assert(NetworkChannel.isCanceledOutput(Serialization.systemErr))
  }

  test("control-plane and flush methods are never dropped while canceling") {
    val kept = Seq(
      Serialization.systemOutFlush,
      Serialization.systemErrFlush,
      Serialization.readSystemIn,
      Serialization.promptChannel,
      "build/logMessage",
      "sbt/exec",
      "window/logMessage",
      sbt.BasicCommandStrings.Shutdown,
    )
    kept.foreach(m => assert(!NetworkChannel.isCanceledOutput(m), s"must not drop: $m"))
  }

end NetworkChannelSpec
