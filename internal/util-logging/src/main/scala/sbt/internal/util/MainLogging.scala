/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import sbt.util._
import java.io.PrintWriter
import org.apache.logging.log4j.core.Appender

object MainAppender {
  import java.util.concurrent.atomic.AtomicInteger
  private def generateGlobalBackingName: String =
    "GlobalBacking" + generateId.incrementAndGet
  private val generateId: AtomicInteger = new AtomicInteger

  def multiLogger(log: ManagedLogger, config: MainAppenderConfig): ManagedLogger = {
    import config._
    // TODO
    // backed setTrace backingTrace
    // multi: Logger

    LogExchange.unbindLoggerAppenders(log.name)
    LogExchange.bindLoggerAppenders(
      log.name,
      (consoleOpt.toList map { appender =>
        appender match {
          case a: ConsoleAppender =>
            a.setTrace(screenTrace)
          case _ => ()
        }
        appender -> screenLevel
      }) :::
        List(backed -> backingLevel) :::
        (extra map { x =>
        (x -> Level.Info)
      })
    )
    log
  }

  def globalDefault(
      console: ConsoleOut
  ): (ManagedLogger, PrintWriter, GlobalLogBacking) => GlobalLogging = {
    lazy val newAppender: (ManagedLogger, PrintWriter, GlobalLogBacking) => GlobalLogging =
      (log, writer, backing) => {
        val backed: Appender = defaultBacked(generateGlobalBackingName)(writer)
        val full = multiLogger(log, defaultMultiConfig(Option(console), backed, Nil))
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
    ConsoleAppender(ConsoleAppender.generateName, console)

  def defaultScreen(
      console: ConsoleOut,
      suppressedMessage: SuppressedTraceContext => Option[String]
  ): Appender = {
    ConsoleAppender(
      ConsoleAppender.generateName,
      Terminal.get,
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
    defaultBacked(generateGlobalBackingName, ConsoleAppender.formatEnabledInEnv)

  def defaultBacked(loggerName: String): PrintWriter => Appender =
    defaultBacked(loggerName, ConsoleAppender.formatEnabledInEnv)

  def defaultBacked(useFormat: Boolean): PrintWriter => Appender =
    defaultBacked(generateGlobalBackingName, useFormat)

  def defaultBacked(loggerName: String, useFormat: Boolean): PrintWriter => Appender =
    to => {
      ConsoleAppender(
        ConsoleAppender.generateName,
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
