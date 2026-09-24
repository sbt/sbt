/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.client

import sjsonnew.support.scalajson.unsafe.Parser
import verify.BasicTestSuite

object NetworkClientCapabilityTest extends BasicTestSuite:

  private def successLog(json: String): Boolean =
    NetworkClient.successLogCapability(Some(Parser.parseUnsafe(json)))

  test("a server that advertises successLog writes the result line"):
    assert(successLog("""{ "capabilities": { "successLog": true } }"""))

  test("a server that advertises it as false does not"):
    assert(!successLog("""{ "capabilities": { "successLog": false } }"""))

  test("a server from before the capability sends no such key"):
    assert(!successLog("""{ "capabilities": { "hoverProvider": false } }"""))

  test("a result with no capabilities at all reads as false"):
    assert(!successLog("""{}"""))

  test("no result at all reads as false"):
    assert(!NetworkClient.successLogCapability(None))

  test("a non-boolean value reads as false rather than failing the handshake"):
    assert(!successLog("""{ "capabilities": { "successLog": "yes" } }"""))
end NetworkClientCapabilityTest
