package sbt
package internal

import org.apache.logging.log4j.message._
import org.apache.logging.log4j.core.{ LogEvent => XLogEvent }
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.layout.PatternLayout
import org.apache.logging.log4j.core.async.RingBufferLogEvent
import sbt.util.Level
import sbt.internal.util._
import sbt.protocol.LogEvent
import sbt.internal.util.codec._

class RelayAppender(name: String) extends AbstractAppender(name, null, PatternLayout.createDefaultLayout(), true) {
  import JsonProtocol._

  def append(event: XLogEvent): Unit =
    {
      val level = ConsoleAppender.toLevel(event.getLevel)
      val message = event.getMessage
      message match {
        case o: ObjectMessage        => appendEvent(level, o.getParameter)
        case p: ParameterizedMessage => appendLog(level, p.getFormattedMessage)
        case r: RingBufferLogEvent   => appendLog(level, r.getFormattedMessage)
        case _                       => appendLog(level, message.toString)
      }
    }
  def appendLog(level: Level.Value, message: => String): Unit = {
    StandardMain.exchange.publishEventMessage(LogEvent(level.toString, message))
  }
  def appendEvent(level: Level.Value, event: AnyRef): Unit =
    event match {
      case x: ChannelLogEntry => StandardMain.exchange.publishEvent(x: AbstractEntry)
    }
}
