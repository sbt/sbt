package sbt

import java.util.concurrent.atomic.AtomicLong
import scala.util.control.NonFatal

private[sbt] object GCUtil {
  // Returns the default force garbage collection flag,
  // as specified by system properties.
  val defaultForceGarbageCollection: Boolean = true
  val defaultMinForcegcInterval: Int = 60
  val lastGcCheck: AtomicLong = new AtomicLong(0L)

  def forceGcWithInterval(minForcegcInterval: Int, log: Logger): Unit =
    {
      val now = System.currentTimeMillis
      val last = lastGcCheck.get
      // This throttles System.gc calls to interval
      if (now - last > minForcegcInterval * 1000) {
        lastGcCheck.set(now)
        forceGc(log)
      }
    }

  def forceGc(log: Logger): Unit =
    try {
      log.debug(s"Forcing garbage collection...")
      // Force the detection of finalizers for scala.reflect weakhashsets
      System.gc()
      // Force finalizers to run.
      System.runFinalization()
      // Force actually cleaning the weak hash maps.
      System.gc()
    } catch {
      case NonFatal(_) => // gotta catch em all
    }
}
