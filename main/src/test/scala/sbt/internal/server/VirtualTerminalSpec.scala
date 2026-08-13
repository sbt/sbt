/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.server

import sbt.protocol.{
  TerminalCapabilitiesQuery,
  TerminalPropertiesResponse,
  TerminalSetAttributesCommand,
  TerminalSetEchoCommand,
  TerminalSetRawModeCommand,
  TerminalSetSizeCommand,
}
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

  test("cancelRequests wakes waiters on every pending terminal map"):
    val name = "drain-test"
    val props = VirtualTerminal.sendTerminalPropertiesQuery(name, (_, _, _) => ())
    val caps = VirtualTerminal.sendTerminalCapabilitiesQuery(
      name,
      (_, _, _) => (),
      TerminalCapabilitiesQuery(None, None, None)
    )
    val attrs = VirtualTerminal.sendTerminalAttributesQuery(name, (_, _, _) => ())
    val setAttrs = VirtualTerminal.setTerminalAttributesCommand(
      name,
      (_, _, _) => (),
      TerminalSetAttributesCommand("", "", "", "", "")
    )
    val setSize =
      VirtualTerminal.setTerminalSize(name, (_, _, _) => (), TerminalSetSizeCommand(80, 24))
    val getSize = VirtualTerminal.getTerminalSize(name, (_, _, _) => ())
    val echo = VirtualTerminal.setTerminalEcho(name, (_, _, _) => (), TerminalSetEchoCommand(true))
    val raw =
      VirtualTerminal.setTerminalRawMode(name, (_, _, _) => (), TerminalSetRawModeCommand(true))
    VirtualTerminal.cancelRequests(name)
    List(props, caps, attrs, setAttrs, setSize, getSize, echo, raw).foreach { q =>
      assert(q.poll() != null)
    }

  test("cancelRequests leaves other channels' waiters parked"):
    val queue = VirtualTerminal.sendTerminalPropertiesQuery("other-channel", (_, _, _) => ())
    VirtualTerminal.cancelRequests("drain-test-2")
    assert(queue.poll() == null)
end VirtualTerminalSpec
