/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import org.apache.logging.log4j.message._
import org.apache.logging.log4j.core.{ LogEvent => XLogEvent }
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.layout.PatternLayout
import org.apache.logging.log4j.core.async.RingBufferLogEvent
import org.apache.logging.log4j.core.config.Property
import sbt.util.Level
import sbt.internal.util._
import sbt.protocol.LogEvent

class RelayAppender(name: String)
    extends AbstractAppender(
      name,
      null,
      PatternLayout.createDefaultLayout(),
      true,
      Property.EMPTY_ARRAY
    ) {
  lazy val exchange = StandardMain.exchange

  def append(event: XLogEvent): Unit = {
    val level = ConsoleAppender.toLevel(event.getLevel)
    val message = event.getMessage
    message match {
      case o: ObjectMessage        => appendEvent(o.getParameter)
      case p: ParameterizedMessage => appendLog(level, p.getFormattedMessage)
      case r: RingBufferLogEvent   => appendLog(level, r.getFormattedMessage)
      case _                       => appendLog(level, message.toString)
    }
  }
  def appendLog(level: Level.Value, message: => String): Unit = {
    exchange.logMessage(LogEvent(level.toString, message))
  }
  def appendEvent(event: AnyRef): Unit =
    event match {
      case x: StringEvent    => exchange.logMessage(LogEvent(level = x.level, message = x.message))
      case x: ObjectEvent[_] => exchange.respondObjectEvent(x)
      case _ =>
        println(s"appendEvent: ${event.getClass}")
        ()
    }
}
