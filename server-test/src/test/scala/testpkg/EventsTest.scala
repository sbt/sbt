/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package testpkg

import scala.concurrent.duration._

// starts svr using server-test/events and perform event related tests
object EventsTest extends AbstractServerTest {
  override val testDirectory: String = "events"

  test("report task failures in case of exceptions") { _ =>
    svr.sendJsonRpc(
      """{ "jsonrpc": "2.0", "id": 11, "method": "sbt/exec", "params": { "commandLine": "hello" } }"""
    )
    assert(svr.waitForString(10.seconds) { s =>
      (s contains """"id":11""") && (s contains """"error":""")
    })
  }

  test("report task failures in case of exceptions") { _ =>
    svr.sendJsonRpc(
      """{ "jsonrpc": "2.0", "id": 11, "method": "sbt/exec", "params": { "commandLine": "hello" } }"""
    )
    assert(svr.waitForString(10.seconds) { s =>
      (s contains """"id":11""") && (s contains """"error":""")
    })
  }

  test("return error if cancelling non-matched task id") { _ =>
    svr.sendJsonRpc(
      """{ "jsonrpc": "2.0", "id":12, "method": "sbt/exec", "params": { "commandLine": "run" } }"""
    )
    svr.sendJsonRpc(
      """{ "jsonrpc": "2.0", "id":13, "method": "sbt/cancelRequest", "params": { "id": "55" } }"""
    )
    assert(svr.waitForString(20.seconds) { s =>
      (s contains """"error":{"code":-32800""")
    })
  }

  test("cancel on-going task with numeric id") { _ =>
    svr.sendJsonRpc(
      """{ "jsonrpc": "2.0", "id":12, "method": "sbt/exec", "params": { "commandLine": "run" } }"""
    )
    assert(svr.waitForString(1.minute) { s =>
      svr.sendJsonRpc(
        """{ "jsonrpc": "2.0", "id":13, "method": "sbt/cancelRequest", "params": { "id": "12" } }"""
      )
      s contains """"result":{"status":"Task cancelled""""
    })
  }

  test("cancel on-going task with string id") { _ =>
    svr.sendJsonRpc(
      """{ "jsonrpc": "2.0", "id": "foo", "method": "sbt/exec", "params": { "commandLine": "run" } }"""
    )
    assert(svr.waitForString(1.minute) { s =>
      svr.sendJsonRpc(
        """{ "jsonrpc": "2.0", "id": "bar", "method": "sbt/cancelRequest", "params": { "id": "foo" } }"""
      )
      s contains """"result":{"status":"Task cancelled""""
    })
  }
}
