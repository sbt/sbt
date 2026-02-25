/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package testpkg

import scala.concurrent.duration.*

class ClientSubscriptionTest extends AbstractServerTest {
  override val testDirectory: String = "handshake"

  test("subscribe-to-all (default) client receives broadcast build/logMessage when command runs") {
    svr.sendJsonRpc(
      """{ "jsonrpc": "2.0", "id": 2, "method": "sbt/exec", "params": { "commandLine": "show name" } }"""
    )
    def isLogMessageNotification(line: String): Boolean =
      line.contains("\"method\":\"build/logMessage\"") || line.contains(
        "\"method\": \"build/logMessage\""
      )
    assert(
      svr.waitForString(10.seconds)(isLogMessageNotification),
      "subscribe-to-all client must receive broadcast build/logMessage when a command produces log output"
    )
  }
}

class ClientNoSubscriptionTest extends AbstractServerTest {
  override val testDirectory: String = "handshake"
  override def subscribeToAllForTest: Boolean = false

  test("non-subscribed client receives build/logMessage for its own command") {
    svr.sendJsonRpc(
      """{ "jsonrpc": "2.0", "id": 2, "method": "sbt/exec", "params": { "commandLine": "show name" } }"""
    )
    def isLogMessageNotification(line: String): Boolean =
      line.contains("\"method\":\"build/logMessage\"") || line.contains(
        "\"method\": \"build/logMessage\""
      )
    assert(
      svr.waitForString(10.seconds)(isLogMessageNotification),
      "non-subscribed client must still receive build/logMessage for its own command"
    )
  }
}
