/* sbt -- Simple Build Tool
 * Copyright 2008, 2009 Mark Harrah
 */
package sbt

import org.apache.ivy.util.{Message, MessageLogger}

trait IvyLogger extends NotNull
{
	def info(msg: => String)
	def debug(msg: => String)
	def warn(msg: => String)
	def error(msg: => String)
	def verbose(msg: => String)
}

/** Interface to Ivy logging. */
private final class IvyLoggerInterface(logger: IvyLogger) extends MessageLogger
{
	def rawlog(msg: String, level: Int) = log(msg, level)
	def log(msg: String, level: Int)
	{
		import Message.{MSG_DEBUG, MSG_VERBOSE, MSG_INFO, MSG_WARN, MSG_ERR}
		level match
		{
			case MSG_DEBUG => debug(msg)
			case MSG_VERBOSE => verbose(msg)
			case MSG_INFO => info(msg)
			case MSG_WARN => warn(msg)
			case MSG_ERR => error(msg)
		}
	}
	def debug(msg: String) = logger.debug(msg)
	def verbose(msg: String) = logger.verbose(msg)
	def deprecated(msg: String) = warn(msg)
	def info(msg: String) = logger.info(msg)
	def rawinfo(msg: String) = info(msg)
	def warn(msg: String) = logger.warn(msg)
	def error(msg: String) =
		if(!msg.startsWith("unknown resolver")) // hack to suppress these kinds of messages
			logger.error(msg)
	
	private def emptyList = java.util.Collections.emptyList[T forSome { type T}]
	def getProblems = emptyList
	def getWarns = emptyList
	def getErrors = emptyList

	def clearProblems = ()
	def sumupProblems = ()
	def progress = ()
	def endProgress = ()

	def endProgress(msg: String) = info(msg)
	def isShowProgress = false
	def setShowProgress(progress: Boolean) {}
}

/** This is a hack to filter error messages about 'unknown resolver ...'. */
private final class SbtMessageLoggerEngine extends MessageLoggerEngine
{
	override def error(msg: String) = if(acceptError(msg)) super.error(msg)
	def acceptError(msg: String) = (msg ne null) && !msg.startsWith("unknown resolver")
}