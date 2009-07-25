package sbt.test

object Reflected
{
	type Logger =
	{
		def enableTrace(flag: Boolean): Unit
		def traceEnabled: Boolean
		def getLevel: Int
		def setLevel(level: Int): Unit

		def trace(t: F0[Throwable]): Unit
		def success(message: F0[String]): Unit
		def log(level: Int, message: F0[String]): Unit
		def control(event: Int, message: F0[String]): Unit
	}
	type F0[T] = 
	{
		def apply(): T
	}
}

final class LocalLogger(logger: Logger) extends NotNull
{
	import Reflected.F0
	def enableTrace(flag: Boolean) = logger.enableTrace(flag)
	def traceEnabled = logger.traceEnabled
	def getLevel = logger.getLevel.id
	def setLevel(level: Int) = logger.setLevel(Level(level))

	def trace(t: F0[Throwable]) = logger.trace(t())
	def success(message: F0[String]) = logger.success(message())
	def log(level: Int, message: F0[String]) = logger.log(Level(level), message())
	def control(event: Int, message: F0[String]) = logger.control(ControlEvent(event), message())
}

final class RemoteLogger(logger: Reflected.Logger) extends Logger
{
	private final class F0[T](s: => T) extends NotNull { def apply(): T = s }
	def getLevel: Level.Value = Level(logger.getLevel)
	def setLevel(newLevel: Level.Value) = logger.setLevel(newLevel.id)
	def enableTrace(flag: Boolean) = logger.enableTrace(flag)
	def traceEnabled = logger.traceEnabled
	
	def trace(t: => Throwable) = logger.trace(new F0(t))
	def success(message: => String) = logger.success(new F0(message))
	def log(level: Level.Value, message: => String) = logger.log(level.id, new F0(message))
	def control(event: ControlEvent.Value, message: => String) = logger.control(event.id, new F0(message))
	def logAll(events: Seq[LogEvent]) = events.foreach(log)
}