/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.client

import sbt.internal.util.Util
import verify.BasicTestSuite

object InteractiveThreadTest extends BasicTestSuite:
  test("clear drops a second wake-up that arrived after the first one was consumed"):
    val interactive = registered()
    interactive.interrupt()
    val woke =
      try
        Thread.sleep(5000)
        false
      catch case _: InterruptedException => true
    interactive.interrupt()
    assert(woke)
    assert(interactive.clear().contains(Thread.currentThread))
    assert(!Thread.interrupted(), "expected the pending wake-up to be dropped")

  test("interrupt doesn't reach a cleared thread"):
    val interactive = registered()
    assert(interactive.clear().contains(Thread.currentThread))
    interactive.interrupt()
    assert(!Thread.interrupted(), "expected no interrupt after clear")

  test("clear keeps an interrupt that didn't come from interrupt"):
    val interactive = registered()
    Thread.currentThread.interrupt()
    val kept =
      try
        interactive.clear()
        Thread.interrupted()
      finally Util.ignoreResult(Thread.interrupted())
    assert(kept, "expected the foreign interrupt to be kept")

  private def registered(): InteractiveThread =
    Util.ignoreResult(Thread.interrupted())
    val interactive = InteractiveThread()
    interactive.set(Thread.currentThread)
    interactive
end InteractiveThreadTest
