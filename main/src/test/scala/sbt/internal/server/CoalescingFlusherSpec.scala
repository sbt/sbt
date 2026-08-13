/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.server

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import verify.BasicTestSuite

object CoalescingFlusherSpec extends BasicTestSuite:

  private def newFlusher(count: AtomicInteger, delayMillis: Long = 20L): CoalescingFlusher =
    new CoalescingFlusher("test-flusher", delayMillis, () => { count.incrementAndGet(); () })

  private def awaitAtLeast(count: AtomicInteger, n: Int): Unit =
    val deadline = System.nanoTime + TimeUnit.SECONDS.toNanos(5)
    while (count.get < n && System.nanoTime < deadline) Thread.sleep(2)

  test("repeated flush calls within the window coalesce into a single drain") {
    val drains = new AtomicInteger(0)
    val flusher = newFlusher(drains)
    try {
      (1 to 50).foreach(_ => flusher.flush())
      awaitAtLeast(drains, 1)
      assert(drains.get == 1, s"expected 1 coalesced drain, got ${drains.get}")
    } finally flusher.close()
  }

  // The regression this fixes: forceFlush used to shut the timer executor down, so all later
  // flushes stopped coalescing (and, in the bad state, stopped draining). It must drain now and
  // leave the timer live.
  test("forceFlush drains immediately and does not disable coalescing") {
    val drains = new AtomicInteger(0)
    val flusher = newFlusher(drains)
    try {
      flusher.forceFlush() // 1 immediate drain
      assert(drains.get == 1)
      (1 to 50).foreach(_ => flusher.flush()) // must still coalesce into 1 scheduled drain
      awaitAtLeast(drains, 2)
      // exactly 2: if forceFlush had torn down the executor, each flush would fall back to an
      // inline drain instead of coalescing, giving 51.
      assert(drains.get == 2, s"expected 2 drains, got ${drains.get}")
    } finally flusher.close()
  }

  test("flush after close drains inline") {
    val drains = new AtomicInteger(0)
    val flusher = newFlusher(drains)
    flusher.close()
    flusher.flush()
    assert(drains.get == 1, s"expected inline drain after close, got ${drains.get}")
  }

end CoalescingFlusherSpec
