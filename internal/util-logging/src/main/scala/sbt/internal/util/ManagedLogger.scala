package sbt.internal.util

import sbt.util._
import org.apache.logging.log4j.{ Logger => XLogger }
import org.apache.logging.log4j.message.ObjectMessage
import sjsonnew.JsonFormat

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
        new ObjectMessage(StringEvent(level.toString, message, channelName, execId))
      )
    }
  override def success(message: => String): Unit = xlogger.info(message)

  final def debugEvent[A: JsonFormat](event: => A): Unit = logEvent(Level.Debug, event)
  final def infoEvent[A: JsonFormat](event: => A): Unit = logEvent(Level.Info, event)
  final def warnEvent[A: JsonFormat](event: => A): Unit = logEvent(Level.Warn, event)
  final def errorEvent[A: JsonFormat](event: => A): Unit = logEvent(Level.Error, event)
  def logEvent[A: JsonFormat](level: Level.Value, event: => A): Unit =
    {
      val v: A = event
      val clazz: Class[A] = v.getClass.asInstanceOf[Class[A]]
      val ev = LogExchange.getOrElseUpdateJsonCodec(clazz, implicitly[JsonFormat[A]])
      val entry: ObjectEvent[A] = new ObjectEvent(level, v, channelName, execId, ev, clazz)
      xlogger.log(
        ConsoleAppender.toXLevel(level),
        new ObjectMessage(entry)
      )
    }
}
