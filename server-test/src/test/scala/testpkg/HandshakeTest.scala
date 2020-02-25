/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package testpkg

import scala.concurrent.duration._

// starts svr using server-test/handshake and perform basic tests
object HandshakeTest extends AbstractServerTest {
  override val testDirectory: String = "handshake"

  test("handshake") { _ =>
    svr.sendJsonRpc(
      """{ "jsonrpc": "2.0", "id": "3", "method": "sbt/setting", "params": { "setting": "root/name" } }"""
    )
    assert(svr.waitForString(10.seconds) { s =>
      s contains """"id":"3""""
    })
  }

  test("return number id when number id is sent") { _ =>
    svr.sendJsonRpc(
      """{ "jsonrpc": "2.0", "id": 3, "method": "sbt/setting", "params": { "setting": "root/name" } }"""
    )
    assert(svr.waitForString(10.seconds) { s =>
      s contains """"id":3"""
    })
  }
}
