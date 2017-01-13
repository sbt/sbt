package sbt.internal.util

import sbt.util._
import org.apache.logging.log4j.{ Logger => XLogger }
import org.apache.logging.log4j.message.ObjectMessage

/**
 * Delegates log events to the associated LogExchange.
 */
class ManagedLogger(
  val name: String,
  val channelName: Option[String],
  val execId: Option[String],
  xlogger: XLogger
) extends Logger {
  override def trace(t: => Throwable): Unit = () // exchange.appendLog(new Trace(t))
  override def log(level: Level.Value, message: => String): Unit =
    {
      xlogger.log(
        ConsoleAppender.toXLevel(level),
        new ObjectMessage(ChannelLogEntry(level.toString, message, channelName, execId))
      )
    }
  override def success(message: => String): Unit = xlogger.info(message)
}
