/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.duration.*
import scala.util.control.NonFatal
import sbt.util.Logger

private[sbt] object GCUtil {
  // Returns the default force garbage collection flag,
  // as specified by system properties.
  val defaultForceGarbageCollection: Boolean = true
  val defaultMinForcegcInterval: Duration = 10.minutes
  val lastGcCheck: AtomicLong = new AtomicLong(System.currentTimeMillis)

  def forceGcWithInterval(minForcegcInterval: Duration, log: Logger): Unit = {
    val now = System.currentTimeMillis
    val last = lastGcCheck.get
    // This throttles System.gc calls to interval
    if (now - last > minForcegcInterval.toMillis) {
      lastGcCheck.lazySet(now)
      forceGc(log)
    }
  }

  def forceGc(log: Logger): Unit =
    try {
      log.debug(s"Forcing garbage collection...")
      // Force the detection of finalizers for scala.reflect weakhashsets
      System.gc()
    } catch {
      case NonFatal(_) => // gotta catch em all
    }
}
