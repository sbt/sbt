/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import java.util.concurrent.ConcurrentHashMap
import scala.sys.process.Process

/**
 * Manages forked processes created by sbt. Any process registered
 * with RunningProcesses can be killed with the killAll method. In
 * particular, this can be used in a signal handler to kill these
 * processes when the user inputs ctrl+c.
 */
private[sbt] object RunningProcesses {
  val active = ConcurrentHashMap.newKeySet[Process]
  def add(process: Process): Unit = active.synchronized {
    active.add(process)
    ()
  }
  def remove(process: Process): Unit = active.synchronized {
    active.remove(process)
    ()
  }
  def killAll(): Unit = active.synchronized {
    active.forEach(_.destroy())
    active.clear()
  }
}
