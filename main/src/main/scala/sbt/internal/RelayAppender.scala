/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import sbt.internal.util.*
import sbt.protocol.LogEvent
import sbt.util.Level

class RelayAppender(override val name: String, targetChannel: Option[String] = None)
    extends ConsoleAppender(
      name,
      ConsoleAppender.Properties.from(ConsoleOut.NullConsoleOut, true, true),
      _ => None
    ) {
  def this(name: String) = this(name, None)

  lazy val exchange = StandardMain.exchange
  override def appendLog(level: Level.Value, message: => String): Unit = {
    val event = LogEvent(level = level.toString, message = message)
    targetChannel match
      case Some(ch) => exchange.logMessage(ch, event)
      case None     => exchange.logMessage(event)
  }
}
