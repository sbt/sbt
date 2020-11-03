/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import scala.annotation.tailrec
import scala.concurrent.duration._

object JoinThread {
  implicit class ThreadOps(val t: Thread) extends AnyVal {
    def joinFor(duration: FiniteDuration): Unit = {
      val deadline = duration.fromNow
      var exception: Option[InterruptedException] = None
      @tailrec def impl(): Unit = {
        try {
          t.interrupt()
          t.join(10)
        } catch { case e: InterruptedException => exception = Some(e) }
        if (t.isAlive && !deadline.isOverdue) impl()
      }
      impl()
      if (t.isAlive) {
        System.err.println(s"Unable to join thread $t after $duration")
        exception.foreach(throw _)
      }
    }
  }
}
