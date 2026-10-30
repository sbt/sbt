/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package client

import sbt.internal.util.Util

/**
 * The thread that runs a client session. Other threads interrupt it to wake it up, sometimes
 * more than once, so `clear` also drops a wake-up that no wait has consumed.
 */
private[client] final class InteractiveThread:
  private var thread: Option[Thread] = None
  private var interrupted = false

  def set(t: Thread): Unit = synchronized:
    thread = Some(t)
    interrupted = false

  def interrupt(): Unit = synchronized:
    thread.foreach: t =>
      interrupted = true
      t.interrupt()

  /** Unregisters the thread, so that `interrupt` no longer reaches it, and returns it. */
  def clear(): Option[Thread] = synchronized:
    val t = thread
    thread = None
    if interrupted && t.contains(Thread.currentThread) then Util.ignoreResult(Thread.interrupted())
    t
end InteractiveThread
