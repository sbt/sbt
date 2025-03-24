/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import sbt.internal.ui.{ UITask, UserThread }
import sbt.internal.util.*
import sjsonnew.JsonFormat

private[sbt] final class ConsoleChannel(
    val name: String,
    override private[sbt] val mkUIThread: (State, CommandChannel) => UITask
) extends CommandChannel {

  def run(s: State): State = s

  def publishBytes(bytes: Array[Byte]): Unit = ()

  def publishEvent[A: JsonFormat](event: A, execId: Option[String]): Unit = ()

  override val userThread: UserThread = new UserThread(this)
  private[sbt] def terminal = Terminal.console
}
private[sbt] object ConsoleChannel {
  private[sbt] def defaultName = "console0"
}
