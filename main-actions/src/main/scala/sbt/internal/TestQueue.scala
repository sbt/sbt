/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.util.concurrent.atomic.{ AtomicBoolean, AtomicInteger }

/**
 * A queue of test-class work units shared by the forked workers of one test group. Thread-safe.
 *
 * Each element of `perFramework` indexes the `taskDefs` vector sent to every worker, one sub-queue
 * per framework: a worker runs frameworks sequentially and must never be handed a class belonging
 * to a framework it is not currently running.
 */
private[sbt] final class TestQueue(perFramework: Vector[Vector[Int]]):
  private val cursors: Vector[AtomicInteger] = perFramework.map(_ => AtomicInteger(0))
  private val poisoned: AtomicBoolean = AtomicBoolean(false)

  def hasWork: Boolean = !poisoned.get() && remaining > 0

  /** Each index goes to at most one caller. */
  def lease(framework: Int): Option[Int] =
    if poisoned.get() || framework < 0 || framework >= perFramework.length then None
    else
      val units = perFramework(framework)
      val i = cursors(framework).getAndIncrement()
      if i < units.length then Some(units(i))
      else None

  /** Stops serving new work; units already leased are unaffected. */
  def poison(): Unit = poisoned.set(true)

  /** Units never leased; non-zero at the end of a run means classes nobody ran. */
  def remaining: Int =
    perFramework.indices.map { f =>
      math.max(0, perFramework(f).length - cursors(f).get())
    }.sum
end TestQueue
