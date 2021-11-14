/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

import sbt.BasicCommandStrings.{ ClearOnFailure, FailureWall }
import sbt.Watched.ContinuousEventMonitor
import sbt.internal.io.{ EventMonitor, WatchState }
import sbt.internal.nio.{ FileEventMonitor, FileTreeRepository, WatchLogger }
import sbt.{ State, Watched }

import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.util.control.NonFatal

private[sbt] object LegacyWatched {
  @deprecated("Replaced by Watched.command", "1.3.0")
  def executeContinuously(watched: Watched, s: State, next: String, repeat: String): State = {
    @tailrec def shouldTerminate: Boolean =
      (System.in.available > 0) && (watched.terminateWatch(System.in.read()) || shouldTerminate)
    val log = s.log
    s get ContinuousEventMonitor match {
      case None =>
        val watchState = WatchState.empty(watched.watchService(), watched.watchSources(s))
        // This is the first iteration, so run the task and create a new EventMonitor
        val logger: WatchLogger = (a: Any) => log.debug(a.toString)
        val repo = FileTreeRepository.legacy(logger, watched.watchService())
        val fileEventMonitor = FileEventMonitor.antiEntropy(
          repo,
          watched.antiEntropy,
          logger,
          watched.antiEntropy,
          10.minutes
        )
        val monitor = new EventMonitor {
          override def awaitEvent(): Boolean = fileEventMonitor.poll(2.millis).nonEmpty
          override def state(): WatchState = watchState
          override def close(): Unit = watchState.close()
        }
        (ClearOnFailure :: next :: FailureWall :: repeat :: s)
          .put(ContinuousEventMonitor, monitor: EventMonitor)
      case Some(eventMonitor) =>
        Watched.printIfDefined(watched watchingMessage eventMonitor.state())
        @tailrec def impl(): State = {
          val triggered = try eventMonitor.awaitEvent()
          catch {
            case NonFatal(e) =>
              log.error(
                "Error occurred obtaining files to watch.  Terminating continuous execution..."
              )
              s.handleError(e)
              false
          }
          if (triggered) {
            Watched.printIfDefined(watched triggeredMessage eventMonitor.state())
            ClearOnFailure :: next :: FailureWall :: repeat :: s
          } else if (shouldTerminate) {
            while (System.in.available() > 0) System.in.read()
            eventMonitor.close()
            s.remove(ContinuousEventMonitor)
          } else {
            impl()
          }
        }
        impl()
    }
  }

}

package io {
  @deprecated("No longer used", "1.3.0")
  private[sbt] trait EventMonitor extends AutoCloseable {

    /** Block indefinitely until the monitor receives a file event or the user stops the watch. */
    def awaitEvent(): Boolean

    /** A snapshot of the WatchState that includes the number of build triggers and watch sources. */
    def state(): WatchState
  }
}
