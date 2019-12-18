/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package testpkg

import scala.concurrent.duration._

// starts svr using server-test/response and perform custom server tests
object ResponseTest extends AbstractServerTest {
  override val testDirectory: String = "response"

  test("response from a command") { _ =>
    svr.sendJsonRpc(
      """{ "jsonrpc": "2.0", "id": "10", "method": "foo/export", "params": {} }"""
    )
    assert(svr.waitForString(10.seconds) { s =>
      if (!s.contains("systemOut")) println(s)
      (s contains """"id":"10"""") &&
      (s contains "scala-library.jar")
    })
  }

  test("response from a task") { _ =>
    svr.sendJsonRpc(
      """{ "jsonrpc": "2.0", "id": "11", "method": "foo/rootClasspath", "params": {} }"""
    )
    assert(svr.waitForString(10.seconds) { s =>
      if (!s.contains("systemOut")) println(s)
      (s contains """"id":"11"""") &&
      (s contains "scala-library.jar")
    })
  }

  test("a command failure") { _ =>
    svr.sendJsonRpc(
      """{ "jsonrpc": "2.0", "id": "12", "method": "foo/fail", "params": {} }"""
    )
    assert(svr.waitForString(10.seconds) { s =>
      if (!s.contains("systemOut")) println(s)
      (s contains """"error":{"code":-33000,"message":"fail message"""")
    })
  }

  test("a command failure with custom code") { _ =>
    svr.sendJsonRpc(
      """{ "jsonrpc": "2.0", "id": "13", "method": "foo/customfail", "params": {} }"""
    )
    assert(svr.waitForString(10.seconds) { s =>
      if (!s.contains("systemOut")) println(s)
      (s contains """"error":{"code":500,"message":"some error"""")
    })
  }

  test("a command with a notification") { _ =>
    svr.sendJsonRpc(
      """{ "jsonrpc": "2.0", "id": "14", "method": "foo/notification", "params": {} }"""
    )
    assert(svr.waitForString(10.seconds) { s =>
      if (!s.contains("systemOut")) println(s)
      (s contains """{"jsonrpc":"2.0","method":"foo/something","params":"something"}""")
    })
  }

  test("respond concurrently from a task and the handler") { _ =>
    svr.sendJsonRpc(
      """{ "jsonrpc": "2.0", "id": "15", "method": "foo/respondTwice", "params": {} }"""
    )
    assert {
      svr.waitForString(1.seconds) { s =>
        if (!s.contains("systemOut")) println(s)
        s contains "\"id\":\"15\""
      }
    }
    assert {
      // the second response should never be sent
      svr.neverReceive(500.milliseconds) { s =>
        if (!s.contains("systemOut")) println(s)
        s contains "\"id\":\"15\""
      }
    }
  }

  test("concurrent result and error") { _ =>
    svr.sendJsonRpc(
      """{ "jsonrpc": "2.0", "id": "16", "method": "foo/resultAndError", "params": {} }"""
    )
    assert {
      svr.waitForString(1.seconds) { s =>
        if (!s.contains("systemOut")) println(s)
        s contains "\"id\":\"16\""
      }
    }
    assert {
      // the second response (result or error) should never be sent
      svr.neverReceive(500.milliseconds) { s =>
        if (!s.contains("systemOut")) println(s)
        s contains "\"id\":\"16\""
      }
    }
  }

  test("response to a notification should not be sent") { _ =>
    svr.sendJsonRpc(
      """{ "jsonrpc": "2.0", "method": "foo/customNotification", "params": {} }"""
    )
    assert {
      svr.neverReceive(500.milliseconds) { s =>
        if (!s.contains("systemOut")) println(s)
        s contains "\"result\":\"notification result\""
      }
    }
  }
}
