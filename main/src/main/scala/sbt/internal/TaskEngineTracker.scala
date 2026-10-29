/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

import sbt.RunningTaskEngine
import sbt.util.Logger
import scala.util.Try

private[sbt] final class TaskEngineTracker(cleanup: Logger => Unit) {
  private var engines = List.empty[RunningTaskEngine]
  private var generation = 0L
  private var cancelled = false
  private var cancelling = false
  private var log: Logger = Logger.Null

  def add(engine: RunningTaskEngine, rootLog: Logger = Logger.Null): () => Unit = {
    val (current, alreadyCancelled) = synchronized {
      if (engines.isEmpty) log = rootLog
      engines = engine :: engines
      (generation, cancelled)
    }
    if (alreadyCancelled) {
      try engine.cancelAndShutdown()
      catch {
        case e: Throwable =>
          remove(engine)
          throw e
      }
    }
    () => cancel(current)
  }

  def remove(engine: RunningTaskEngine): Unit = synchronized {
    if (engines.nonEmpty) {
      engines = engines.filterNot(_ eq engine)
      if (engines.isEmpty) {
        generation += 1
        cancelled = false
        cancelling = false
        log = Logger.Null
      }
    }
  }

  def currentCancellation: Option[() => Unit] = synchronized {
    if (engines.isEmpty) None
    else {
      val current = generation
      Some(() => cancel(current))
    }
  }

  private def cancel(current: Long): Unit = {
    val snapshot = synchronized {
      if (current != generation || engines.isEmpty || cancelling) None
      else {
        cancelled = true
        cancelling = true
        Some((engines.reverse, log))
      }
    }
    snapshot.foreach {
      case (active, rootLog) =>
        try {
          val common = Try(cleanup(rootLog))
          val shutdowns = active.map(engine => Try(engine.cancelAndShutdown()))
          (common :: shutdowns).foreach(_.get)
        } finally {
          synchronized {
            if (current == generation) cancelling = false
          }
        }
    }
  }
}
