/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package server

import java.util.concurrent.{ Executors, Future, RejectedExecutionException, TimeUnit }
import java.util.concurrent.atomic.AtomicReference
import sbt.internal.util.Util

/**
 * Coalesces calls to a `drain` callback so it runs at most once per `delayMillis` window, on one
 * dedicated thread. This batches remote-client writes to cut terminal flicker and byte volume.
 *
 * [[forceFlush]] runs the drain immediately (e.g. to order buffered stdout before a control-plane
 * message) and leaves the timer live, so coalescing keeps working; a pending coalesced drain
 * harmlessly drains the remainder when it fires. Only [[close]] shuts the thread down.
 */
private[server] final class CoalescingFlusher(
    threadName: String,
    delayMillis: Long,
    drain: () => Unit
) extends AutoCloseable:
  private val executor = Executors.newSingleThreadScheduledExecutor(r => new Thread(r, threadName))
  private val pending = new AtomicReference[Future[?]]

  /**
   * Schedule a coalesced drain if none is already pending; drain inline if the timer is closed.
   * Not safe for concurrent callers (the check-then-schedule on `pending` is not atomic); the sole
   * caller serializes its writes upstream.
   */
  def flush(): Unit =
    pending.get match
      case null =>
        try
          pending.set(
            executor.schedule(
              (() => { pending.set(null); drain() }): Runnable,
              delayMillis,
              TimeUnit.MILLISECONDS
            )
          )
        catch case _: RejectedExecutionException => drain()
      case _ => ()

  /** Drain now, leaving the timer intact so subsequent [[flush]] calls still coalesce. */
  def forceFlush(): Unit = drain()

  def close(): Unit = Util.ignoreResult(executor.shutdownNow())

end CoalescingFlusher
