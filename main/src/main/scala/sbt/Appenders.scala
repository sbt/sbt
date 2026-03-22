/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import sbt.internal.util.{ Appender, ConsoleAppender as InternalConsoleAppender, ConsoleOut }
import java.io.{ PrintStream, PrintWriter }

object Appenders {
  def consoleAppender(): Appender = InternalConsoleAppender()
  def consoleAppender(out: PrintStream): Appender = InternalConsoleAppender(out)
  def consoleAppender(out: PrintWriter): Appender = InternalConsoleAppender(out)
  def consoleAppender(name: String): Appender = InternalConsoleAppender(name)
  def consoleAppender(name: String, out: ConsoleOut): Appender = InternalConsoleAppender(name, out)
  def consoleAppender(name: String, out: PrintWriter): Appender = InternalConsoleAppender(name, ConsoleOut.printWriterOut(out))
}
