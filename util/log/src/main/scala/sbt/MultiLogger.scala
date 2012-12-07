
/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010 Mark Harrah
 */
package sbt

// note that setting the logging level on this logger has no effect on its behavior, only
//   on the behavior of the delegates.
class MultiLogger(delegates: List[AbstractLogger]) extends BasicLogger
{
	override lazy val ansiCodesSupported = delegates exists supported
	private[this] lazy val allSupportCodes = delegates forall supported
	private[this] def supported = (_: AbstractLogger).ansiCodesSupported

	override def setLevel(newLevel: Level.Value)
	{
		super.setLevel(newLevel)
		dispatch(new SetLevel(newLevel))
	}
	override def setTrace(level: Int)
	{
		super.setTrace(level)
		dispatch(new SetTrace(level))
	}
	override def setSuccessEnabled(flag: Boolean)
	{
		super.setSuccessEnabled(flag)
		dispatch(new SetSuccess(flag))
	}
	def trace(t: => Throwable) { dispatch(new Trace(t)) }
	def log(level: Level.Value, message: => String) { dispatch(new Log(level, message)) }
	def success(message: => String) { dispatch(new Success(message)) }
	def logAll(events: Seq[LogEvent]) { delegates.foreach(_.logAll(events)) }
	def control(event: ControlEvent.Value, message: => String) { delegates.foreach(_.control(event, message)) }
	private[this] def dispatch(event: LogEvent)
	{
		val plainEvent = if(allSupportCodes) event else removeEscapes(event)
		for( d <- delegates)
			if(d.ansiCodesSupported)
				d.log(event)
			else
				d.log(plainEvent)
	}

	private[this] def removeEscapes(event: LogEvent): LogEvent =
	{
		import ConsoleLogger.{removeEscapeSequences => rm}
		event match {
			case s: Success => new Success(rm(s.msg))
			case l: Log => new Log(l.level, rm(l.msg))
			case ce: ControlEvent => new ControlEvent(ce.event, rm(ce.msg))
			case _: Trace | _: SetLevel | _: SetTrace | _: SetSuccess => event
		}
	}
}