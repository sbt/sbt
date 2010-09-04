
/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010 Mark Harrah
 */
package sbt

// note that setting the logging level on this logger has no effect on its behavior, only
//   on the behavior of the delegates.
class MultiLogger(delegates: List[AbstractLogger]) extends BasicLogger
{
	override lazy val ansiCodesSupported = delegates.forall(_.ansiCodesSupported)
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
	def trace(t: => Throwable) { dispatch(new Trace(t)) }
	def log(level: Level.Value, message: => String) { dispatch(new Log(level, message)) }
	def success(message: => String) { dispatch(new Success(message)) }
	def logAll(events: Seq[LogEvent]) { delegates.foreach(_.logAll(events)) }
	def control(event: ControlEvent.Value, message: => String) { delegates.foreach(_.control(event, message)) }
	private def dispatch(event: LogEvent) { delegates.foreach(_.log(event)) }
}