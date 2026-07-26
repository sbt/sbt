/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.server

import sbt.protocol.TerminalPropertiesResponse
import verify.BasicTestSuite

object VirtualTerminalSpec extends BasicTestSuite:
  test("expiring an unanswered properties query deregisters it"):
    val queue = VirtualTerminal.sendTerminalPropertiesQuery("expire-test", (_, _, _) => ())
    assert(VirtualTerminal.expireTerminalPropertiesQuery("expire-test", queue).isEmpty)
    // Once expired, the registration is gone: a channel-wide cancel must not touch the queue.
    VirtualTerminal.cancelRequests("expire-test")
    assert(queue.poll() == null)

  test("expiring rescues a response that raced in before deregistration"):
    val queue = VirtualTerminal.sendTerminalPropertiesQuery("expire-race", (_, _, _) => ())
    val r = TerminalPropertiesResponse(80, 24, true, true, true, true)
    queue.put(r)
    assert(VirtualTerminal.expireTerminalPropertiesQuery("expire-race", queue) == Some(r))
end VirtualTerminalSpec
