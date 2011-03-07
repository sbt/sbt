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
	def successEnabled = successEnabledVar
	def setSuccessEnabled(flag: Boolean) { successEnabledVar = flag }
	def getLevel = level
	def setLevel(newLevel: Level.Value) { level = newLevel }
	def setTrace(level: Int) { traceEnabledVar = level }
	def getTrace = traceEnabledVar
}