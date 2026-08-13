/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package testpkg

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.*
import sbt.internal.util.Util
import sbt.protocol.{ Attach, FaultyTerminalSession, Serialization }
import sbt.protocol.codec.JsonProtocol.given

/**
 * Regression: one attached client that answers the terminal-properties query with a
 * malformed response must not freeze the server for every other client.
 */
class TerminalPropertiesFreezeTest extends AbstractServerTest {
  override val testDirectory: String = "client"

  test("a client with a broken terminal-properties response does not freeze the server") {
    val portfile = new java.io.File(testPath.toFile, "project/target/active.json")
    val faulty = FaultyTerminalSession.connect(portfile)
    try {
      faulty.initialize(10.seconds, false).get
      Util.ignoreResult(
        faulty.sendJsonRpc(faulty.nextId(), Serialization.attach, Attach(interactive = true))
      )
      assert(
        faulty.propertiesQueried.await(30, TimeUnit.SECONDS),
        "server never sent the terminal-properties query"
      )
      assert(runBatchClient("willSucceed") == 0, "a well-behaved client must still be served")
      assert(runBatchClient("willSucceed") == 0, "the server must stay serviceable")
    } finally faulty.close()
  }
}
