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

class RelayAppender(override val name: String)
    extends ConsoleAppender(
      name,
      ConsoleAppender.Properties.from(ConsoleOut.NullConsoleOut, true, true),
      _ => None
    ) {
  lazy val exchange = StandardMain.exchange
  override def appendLog(level: Level.Value, message: => String): Unit = {
    exchange.logMessage(LogEvent(level = level.toString, message = message))
  }
}
