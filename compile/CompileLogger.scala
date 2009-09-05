package xsbt

trait CompileLogger extends xsbti.Logger with NotNull
{
	def info(msg: => String)
	def debug(msg: => String)
	def warn(msg: => String)
	def error(msg: => String)
	def trace(t: => Throwable)
}
