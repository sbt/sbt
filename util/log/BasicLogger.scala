/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010 Mark Harrah
 */
package sbt

/** Implements the level-setting methods of Logger.*/
abstract class BasicLogger extends AbstractLogger
{
	private var traceEnabledVar = java.lang.Integer.MAX_VALUE
	private var level: Level.Value = Level.Info
	private var successEnabledVar = true
	def successEnabled = synchronized { successEnabledVar }
	def setSuccessEnabled(flag: Boolean): Unit = synchronized { successEnabledVar = flag }
	def getLevel = synchronized { level }
	def setLevel(newLevel: Level.Value): Unit = synchronized { level = newLevel }
	def setTrace(level: Int): Unit = synchronized { traceEnabledVar = level }
	def getTrace = synchronized { traceEnabledVar }
}