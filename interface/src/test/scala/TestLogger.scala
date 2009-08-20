package xsbti

class TestLogger extends Logger
{
	private val buffer = new scala.collection.mutable.ArrayBuffer[F0[Unit]]
	def info(msg: F0[String]) = buffer("[info] ", msg)
	def warn(msg: F0[String]) = buffer("[warn] ", msg)
	def debug(msg: F0[String]) = buffer("[debug] ", msg)
	def error(msg: F0[String]) = buffer("[error] ", msg)
	def verbose(msg: F0[String]) = buffer("[verbose] ", msg)
	def info(msg: => String) = buffer("[info] ", msg)
	def warn(msg: => String) = buffer("[warn] ", msg)
	def debug(msg: => String) = buffer("[debug] ", msg)
	def error(msg: => String) = buffer("[error] ", msg)
	def verbose(msg: => String) = buffer("[verbose] ", msg)
	def show() { buffer.foreach(_()) }
	def clear() { buffer.clear() }
	def trace(t: F0[Throwable]) { buffer += f0(t().printStackTrace) }
	private def buffer(s: String, msg: F0[String]) { buffer(s, msg()) }
	private def buffer(s: String, msg: => String) { buffer += f0(println(s + msg)) }
}
object TestLogger
{
	def apply[T](f: Logger => T): T =
	{
		val log = new TestLogger
		try { f(log) }
		catch { case e: Exception => log.show(); throw e }
		finally { log.clear() }
	}
	def apply[L <: TestLogger, T](newLogger:  => L)(f: L => T): T =
	{
		val log = newLogger
		try { f(log) }
		catch { case e: Exception => log.show(); throw e }
		finally { log.clear() }
	}
}