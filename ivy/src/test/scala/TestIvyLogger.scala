package sbt

import xsbt.{BufferedLogger, ConsoleLogger, Level}

class TestIvyLogger extends BufferedLogger(new ConsoleLogger) with IvyLogger { def verbose(msg: => String) = info(msg) }
object TestIvyLogger
{
	def apply[T](f: IvyLogger => T): T =
	{
		val log = new TestIvyLogger
		log.setLevel(Level.Debug)
		log.bufferQuietly(f(log))
	}
}