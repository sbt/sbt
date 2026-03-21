/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package testpkg

import sbt.protocol.SettingQuery
import sbt.protocol.codec.JsonProtocol.given
import sbt.protocol.SettingQuerySuccess
import sjsonnew.shaded.scalajson.ast.unsafe.JString

// starts svr using server-test/handshake and perform basic tests
class HandshakeTest extends AbstractServerTest {
  override val testDirectory: String = "handshake"

  test("handshake") {
    val response = svr.session
      .sendJsonRpcAwaitResult[SettingQuerySuccess]("sbt/setting", SettingQuery("root/name"))
      .get
    assert(response.value == JString("handshake"))
  }

  test("return number id when number id is sent") {
    val response = svr.session
      .sendJsonRpcAwaitResult[SettingQuerySuccess]("sbt/setting", SettingQuery("root/name"))
      .get
    assert(response.value == JString("handshake"))
  }
}
