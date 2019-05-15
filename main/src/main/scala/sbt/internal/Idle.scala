/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

import scala.concurrent.duration._
import scala.util.control.NonFatal

private[sbt] object Idle {
  val value: FiniteDuration =
    try System.getProperty("sbt.idle.seconds", "300").toInt.seconds
    catch { case NonFatal(_) => 5.minute }
  val idleTimeout: FiniteDuration = 500.millis
  val activeTimout: FiniteDuration = 20.millis
}
