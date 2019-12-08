/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010 Mark Harrah
 */
package sbt.internal.util

import sbt.util._

/** Implements the level-setting methods of Logger.*/
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
