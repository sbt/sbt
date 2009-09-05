/* sbt -- Simple Build Tool
 * Copyright 2008, 2009 Mark Harrah
 */
 package xsbt

/** Implements the level-setting methods of Logger.*/
abstract class BasicLogger extends Logger
{
	private var traceEnabledVar = true
	private var level: Level.Value = Level.Info
	def getLevel = level
	def setLevel(newLevel: Level.Value) { level = newLevel }
	def enableTrace(flag: Boolean) { traceEnabledVar = flag }
	def traceEnabled = traceEnabledVar
}
