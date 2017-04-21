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
import scala.json.ast.unsafe._

class RelayAppender(name: String)
    extends AbstractAppender(name, null, PatternLayout.createDefaultLayout(), true) {
  lazy val exchange = StandardMain.exchange
  lazy val jsonFormat = new sjsonnew.BasicJsonProtocol with JValueFormats {}

  def append(event: XLogEvent): Unit = {
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
    exchange.publishEventMessage(LogEvent(level.toString, message))
  }
  def appendEvent(level: Level.Value, event: AnyRef): Unit =
    event match {
      case x: StringEvent => {
        import JsonProtocol._
        exchange.publishEvent(x: AbstractEntry)
      }
      case x: ObjectEvent[_] => {
        import jsonFormat._
        val json = JObject(
          JField("type", JString(x.contentType)),
          (Vector(JField("message", x.json), JField("level", JString(x.level.toString))) ++
            (x.channelName.toVector map { channelName =>
              JField("channelName", JString(channelName))
            }) ++
            (x.execId.toVector map { execId =>
              JField("execId", JString(execId))
            })): _*
        )
        exchange.publishEvent(json: JValue)
      }
      case _ =>
        println(s"appendEvent: ${event.getClass}")
        ()
    }
}
