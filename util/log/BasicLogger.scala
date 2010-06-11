/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010 Mark Harrah
 */
package sbt

/** Implements the level-setting methods of Logger.*/
abstract class BasicLogger extends AbstractLogger
{
	private var traceEnabledVar = java.lang.Integer.MAX_VALUE
	private var level: Level.Value = Level.Info
	def getLevel = level
	def setLevel(newLevel: Level.Value) { level = newLevel }
	def setTrace(level: Int) { traceEnabledVar = level }
	def getTrace = traceEnabledVar
}