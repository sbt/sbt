/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package testpkg

import scala.concurrent.duration.*
import sbt.internal.langserver.SbtExecParams
import sbt.internal.langserver.codec.JsonProtocol.given

class ClientSubscriptionTest extends AbstractServerTest {
  override val testDirectory: String = "handshake"

  test("subscribe-to-all (default) client receives broadcast build/logMessage when command runs") {
    val id = svr.session.nextId()
    svr.session.sendJsonRpc(id, "sbt/exec", SbtExecParams("show name")).get
    svr.session.waitForNotificationMsg(10.seconds)(_.method == "build/logMessage").get
  }
}

class ClientNoSubscriptionTest extends AbstractServerTest {
  override val testDirectory: String = "handshake"
  override def subscribeToAllForTest: Boolean = false

  test("non-subscribed client receives build/logMessage for its own command") {
    val id = svr.session.nextId()
    svr.session.sendJsonRpc(id, "sbt/exec", SbtExecParams("show name")).get
    svr.session.waitForNotificationMsg(10.seconds)(_.method == "build/logMessage").get
  }
}
