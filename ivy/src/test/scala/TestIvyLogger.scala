package sbt

class TestIvyLogger extends BufferedLogger(new ConsoleLogger) with IvyLogger
object TestIvyLogger
{
	def apply[T](f: IvyLogger => T): T =
	{
		val log = new TestIvyLogger
		log.setLevel(Level.Debug)
		log.bufferQuietly(f(log))
	}
}