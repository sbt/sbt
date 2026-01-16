/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import scala.annotation.tailrec
import scala.concurrent.duration.*
import java.util.concurrent.TimeoutException

object JoinThread {
  extension (t: Thread) {
    def joinFor(duration: FiniteDuration): Unit = {
      val deadline = duration.fromNow
      @tailrec def impl(): Unit = {
        try {
          t.interrupt()
          t.join(10)
        } catch { case e: InterruptedException => }
        if (t.isAlive && !deadline.isOverdue()) impl()
      }
      impl()
      if (t.isAlive) {
        throw new TimeoutException(s"Unable to join thread $t after $duration")
      }
    }
  }
}
