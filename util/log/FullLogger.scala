/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

/** Promotes the simple Logger interface to the full AbstractLogger interface. */
class FullLogger(delegate: Logger, override val ansiCodesSupported: Boolean = false) extends BasicLogger
{
	def trace(t: => Throwable)
	{
		if(traceEnabled)
			delegate.trace(t)
	}
	def log(level: Level.Value, message: => String)
	{
		if(atLevel(level))
			delegate.log(level, message)
	}
	def success(message: => String): Unit =
		info(message)
	def control(event: ControlEvent.Value, message: => String): Unit =
		info(message)
	def logAll(events: Seq[LogEvent]): Unit = events.foreach(log)
}
