/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah
 */
package sbt

import org.apache.ivy.util.{ Message, MessageLogger, MessageLoggerEngine }

/** Interface to Ivy logging. */
private final class IvyLoggerInterface(logger: Logger) extends MessageLogger {
  def rawlog(msg: String, level: Int): Unit = log(msg, level)
  def log(msg: String, level: Int): Unit = {
    import Message.{ MSG_DEBUG, MSG_ERR, MSG_INFO, MSG_VERBOSE, MSG_WARN }
    level match {
      case MSG_DEBUG   => debug(msg)
      case MSG_VERBOSE => verbose(msg)
      case MSG_INFO    => info(msg)
      case MSG_WARN    => warn(msg)
      case MSG_ERR     => error(msg)
    }
  }
  //DEBUG level messages are very verbose and rarely useful to users.
  // TODO: provide access to this information some other way
  def debug(msg: String): Unit = ()
  def verbose(msg: String): Unit = logger.verbose(msg)
  def deprecated(msg: String): Unit = warn(msg)
  def info(msg: String): Unit = logger.info(msg)
  def rawinfo(msg: String): Unit = info(msg)
  def warn(msg: String): Unit = logger.warn(msg)
  def error(msg: String): Unit = if (SbtIvyLogger.acceptError(msg)) logger.error(msg)

  private def emptyList = java.util.Collections.emptyList[String]
  def getProblems = emptyList
  def getWarns = emptyList
  def getErrors = emptyList

  def clearProblems(): Unit = ()
  def sumupProblems(): Unit = clearProblems()
  def progress(): Unit = ()
  def endProgress(): Unit = ()

  def endProgress(msg: String): Unit = info(msg)
  def isShowProgress = false
  def setShowProgress(progress: Boolean): Unit = ()
}
private final class SbtMessageLoggerEngine extends MessageLoggerEngine {

  /** This is a hack to filter error messages about 'unknown resolver ...'. */
  override def error(msg: String): Unit = if (SbtIvyLogger.acceptError(msg)) super.error(msg)
  override def sumupProblems(): Unit = clearProblems()
}
private object SbtIvyLogger {
  val UnknownResolver = "unknown resolver"
  def acceptError(msg: String) = (msg ne null) && !msg.startsWith(UnknownResolver)
}
