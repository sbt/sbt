/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

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

class RelayAppender(name: String)
    extends AbstractAppender(name, null, PatternLayout.createDefaultLayout(), true) {
  lazy val exchange = StandardMain.exchange

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
      case x: ObjectEvent[_] => exchange.publishObjectEvent(x)
      case _ =>
        println(s"appendEvent: ${event.getClass}")
        ()
    }
}
