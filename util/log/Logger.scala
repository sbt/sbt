/* sbt -- Simple Build Tool
 * Copyright 2008, 2009 Mark Harrah
 */
 package sbt

	import xsbti.{Logger => xLogger, F0}

abstract class AbstractLogger extends xLogger with NotNull
{
	def getLevel: Level.Value
	def setLevel(newLevel: Level.Value)
	def setTrace(flag: Int)
	def getTrace: Int
	final def traceEnabled = getTrace >= 0
	def ansiCodesSupported = false

	def atLevel(level: Level.Value) = level.id >= getLevel.id
	def trace(t: => Throwable): Unit
	final def verbose(message: => String): Unit = debug(message)
	final def debug(message: => String): Unit = log(Level.Debug, message)
	final def info(message: => String): Unit = log(Level.Info, message)
	final def warn(message: => String): Unit = log(Level.Warn, message)
	final def error(message: => String): Unit = log(Level.Error, message)
	def success(message: => String): Unit
	def log(level: Level.Value, message: => String): Unit
	def control(event: ControlEvent.Value, message: => String): Unit

	def logAll(events: Seq[LogEvent]): Unit
	/** Defined in terms of other methods in Logger and should not be called from them. */
	final def log(event: LogEvent)
	{
		event match
		{
			case s: Success => success(s.msg)
			case l: Log => log(l.level, l.msg)
			case t: Trace => trace(t.exception)
			case setL: SetLevel => setLevel(setL.newLevel)
			case setT: SetTrace => setTrace(setT.level)
			case c: ControlEvent => control(c.event, c.msg)
		}
	}

	def debug(msg: F0[String]): Unit = log(Level.Debug, msg)
	def warn(msg: F0[String]): Unit = log(Level.Warn, msg)
	def info(msg: F0[String]): Unit = log(Level.Info, msg)
	def error(msg: F0[String]): Unit = log(Level.Error, msg)
	def trace(msg: F0[Throwable]) = trace(msg.apply)
	def log(level: Level.Value, msg: F0[String]): Unit = log(level, msg.apply)
}
