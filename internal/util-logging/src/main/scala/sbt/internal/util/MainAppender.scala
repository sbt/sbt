/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import sbt.util.*
import java.io.PrintWriter

object MainAppender {
  import java.util.concurrent.atomic.AtomicInteger
  private def generateGlobalBackingName: String =
    "GlobalBacking" + generateId.incrementAndGet
  private val generateId: AtomicInteger = new AtomicInteger

  def multiLogger(
      log: ManagedLogger,
      config: MainAppenderConfig,
      context: LoggerContext
  ): ManagedLogger = {
    import config.*
    // TODO
    // backed setTrace backingTrace
    // multi: Logger

    context.clearAppenders(log.name)
    consoleOpt match {
      case Some(a: ConsoleAppender) =>
        a.setTrace(screenTrace)
        context.addAppender(log.name, a -> screenLevel)
      case _ =>
    }
    context.addAppender(log.name, backed -> backingLevel)
    extra.foreach(a => context.addAppender(log.name, a -> Level.Info))
    log
  }

  def globalDefault(
      console: ConsoleOut
  ): (ManagedLogger, PrintWriter, GlobalLogBacking, LoggerContext) => GlobalLogging = {
    lazy val newAppender
        : (ManagedLogger, PrintWriter, GlobalLogBacking, LoggerContext) => GlobalLogging =
      (log, writer, backing, lc) => {
        val backed: Appender = defaultBacked(generateGlobalBackingName)(writer)
        val full = multiLogger(log, defaultMultiConfig(Option(console), backed, Nil), lc)
        GlobalLogging(full, console, backed, backing, newAppender)
      }
    newAppender
  }

  def defaultMultiConfig(
      consoleOpt: Option[ConsoleOut],
      backing: Appender,
      extra: List[Appender]
  ): MainAppenderConfig =
    MainAppenderConfig(
      consoleOpt map { defaultScreen(_, ConsoleAppender.noSuppressedMessage) },
      backing,
      extra,
      Level.Info,
      Level.Debug,
      -1,
      Int.MaxValue
    )

  def defaultScreen(console: ConsoleOut): Appender =
    ConsoleAppender(ConsoleAppender.generateName(), console)

  def defaultScreen(
      console: ConsoleOut,
      suppressedMessage: SuppressedTraceContext => Option[String]
  ): Appender = {
    ConsoleAppender(
      ConsoleAppender.generateName(),
      console,
      suppressedMessage = suppressedMessage
    )
  }

  def defaultScreen(
      name: String,
      console: ConsoleOut,
      suppressedMessage: SuppressedTraceContext => Option[String]
  ): Appender =
    ConsoleAppender(name, console, suppressedMessage = suppressedMessage)

  def defaultBacked: PrintWriter => Appender =
    defaultBacked(generateGlobalBackingName, Terminal.isAnsiSupported)

  def defaultBacked(loggerName: String): PrintWriter => Appender =
    defaultBacked(loggerName, Terminal.isAnsiSupported)

  def defaultBacked(useFormat: Boolean): PrintWriter => Appender =
    defaultBacked(generateGlobalBackingName, useFormat)

  def defaultBacked(loggerName: String, useFormat: Boolean): PrintWriter => Appender =
    to => {
      ConsoleAppender(
        ConsoleAppender.generateName(),
        ConsoleOut.printWriterOut(to),
        useFormat = useFormat
      )
    }

  final case class MainAppenderConfig(
      consoleOpt: Option[Appender],
      backed: Appender,
      extra: List[Appender],
      screenLevel: Level.Value,
      backingLevel: Level.Value,
      screenTrace: Int,
      backingTrace: Int
  )
}
