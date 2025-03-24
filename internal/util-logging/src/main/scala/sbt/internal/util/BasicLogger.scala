/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import sbt.util.*

/** Implements the level-setting methods of Logger. */
abstract class BasicLogger extends AbstractLogger {
  private var traceEnabledVar: Int = java.lang.Integer.MAX_VALUE
  private var level: Level.Value = Level.Info
  private var successEnabledVar = true
  def successEnabled: Boolean = synchronized { successEnabledVar }
  def setSuccessEnabled(flag: Boolean): Unit = synchronized { successEnabledVar = flag }
  def getLevel: Level.Value = synchronized { level }
  def setLevel(newLevel: Level.Value): Unit = synchronized { level = newLevel }
  def setTrace(level: Int): Unit = synchronized { traceEnabledVar = level }
  def getTrace: Int = synchronized { traceEnabledVar }
}
