/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

import sbt.BasicCommandStrings.{ ClearOnFailure, FailureWall }
import sbt.internal.io.{ EventMonitor, WatchState }
import sbt.{ State, Watched }

import scala.annotation.tailrec
import Watched.ContinuousEventMonitor

import scala.util.control.NonFatal

private[sbt] object LegacyWatched {
  @deprecated("Replaced by Watched.command", "1.3.0")
  def executeContinuously(watched: Watched, s: State, next: String, repeat: String): State = {
    @tailrec def shouldTerminate: Boolean =
      (System.in.available > 0) && (watched.terminateWatch(System.in.read()) || shouldTerminate)
    val log = s.log
    val logger = new EventMonitor.Logger {
      override def debug(msg: => Any): Unit = log.debug(msg.toString)
    }
    s get ContinuousEventMonitor match {
      case None =>
        // This is the first iteration, so run the task and create a new EventMonitor
        (ClearOnFailure :: next :: FailureWall :: repeat :: s)
          .put(
            ContinuousEventMonitor,
            EventMonitor(
              WatchState.empty(watched.watchService(), watched.watchSources(s)),
              watched.pollInterval,
              watched.antiEntropy,
              shouldTerminate,
              logger
            )
          )
      case Some(eventMonitor) =>
        Watched.printIfDefined(watched watchingMessage eventMonitor.state)
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
          Watched.printIfDefined(watched triggeredMessage eventMonitor.state)
          ClearOnFailure :: next :: FailureWall :: repeat :: s
        } else {
          while (System.in.available() > 0) System.in.read()
          eventMonitor.close()
          s.remove(ContinuousEventMonitor)
        }
    }
  }

}
