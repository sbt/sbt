/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt
package internal

import java.io.PrintWriter
import Def.ScopedKey
import Scope.GlobalScope
import Keys.{ logLevel, logManager, persistLogLevel, persistTraceLevel, sLog, traceLevel }
import scala.Console.{ BLUE, RESET }
import sbt.internal.util.{
  AttributeKey,
  ConsoleOut,
  Settings,
  SuppressedTraceContext,
  MainAppender
}
import MainAppender._
import sbt.util.{ Level, Logger, LogExchange }
import sbt.internal.util.ManagedLogger
import org.apache.logging.log4j.core.Appender

sealed abstract class LogManager {
  def apply(
      data: Settings[Scope],
      state: State,
      task: ScopedKey[_],
      writer: PrintWriter
  ): ManagedLogger

  def backgroundLog(data: Settings[Scope], state: State, task: ScopedKey[_]): ManagedLogger
}

object LogManager {
  import java.util.concurrent.atomic.AtomicInteger
  private val generateId: AtomicInteger = new AtomicInteger

  // This is called by mkStreams
  def construct(
      data: Settings[Scope],
      state: State
  ): (ScopedKey[_], PrintWriter) => ManagedLogger =
    (task: ScopedKey[_], to: PrintWriter) => {
      val manager: LogManager =
        (logManager in task.scope).get(data) getOrElse defaultManager(state.globalLogging.console)
      manager(data, state, task, to)
    }

  def constructBackgroundLog(
      data: Settings[Scope],
      state: State
  ): (ScopedKey[_]) => ManagedLogger =
    (task: ScopedKey[_]) => {
      val manager: LogManager =
        (logManager in task.scope).get(data) getOrElse defaultManager(state.globalLogging.console)
      manager.backgroundLog(data, state, task)
    }

  def defaultManager(console: ConsoleOut): LogManager =
    withLoggers((_, _) => defaultScreen(console))

  // This is called by Defaults.
  def defaults(extra: ScopedKey[_] => Seq[Appender], console: ConsoleOut): LogManager =
    withLoggers(
      (task, state) => defaultScreen(console, suppressedMessage(task, state)),
      extra = extra
    )

  def withScreenLogger(mk: (ScopedKey[_], State) => Appender): LogManager =
    withLoggers(screen = mk)

  def withLoggers(
      screen: (ScopedKey[_], State) => Appender = (_, s) => defaultScreen(s.globalLogging.console),
      backed: PrintWriter => Appender = defaultBacked,
      relay: Unit => Appender = defaultRelay,
      extra: ScopedKey[_] => Seq[Appender] = _ => Nil
  ): LogManager = new DefaultLogManager(screen, backed, relay, extra)

  private class DefaultLogManager(
      screen: (ScopedKey[_], State) => Appender,
      backed: PrintWriter => Appender,
      relay: Unit => Appender,
      extra: ScopedKey[_] => Seq[Appender]
  ) extends LogManager {
    def apply(
        data: Settings[Scope],
        state: State,
        task: ScopedKey[_],
        to: PrintWriter
    ): ManagedLogger =
      defaultLogger(
        data,
        state,
        task,
        screen(task, state),
        backed(to),
        relay(()),
        extra(task).toList
      )

    def backgroundLog(data: Settings[Scope], state: State, task: ScopedKey[_]): ManagedLogger = {
      val console = screen(task, state)
      LogManager.backgroundLog(data, state, task, console, relay(()), extra(task).toList)
    }
  }

  // This is the main function that is used to generate the logger for tasks.
  def defaultLogger(
      data: Settings[Scope],
      state: State,
      task: ScopedKey[_],
      console: Appender,
      backed: Appender,
      relay: Appender,
      extra: List[Appender]
  ): ManagedLogger = {
    val execOpt = state.currentCommand
    val loggerName: String = s"${task.key.label}-${generateId.incrementAndGet}"
    val channelName: Option[String] = execOpt flatMap (_.source map (_.channelName))
    val execId: Option[String] = execOpt flatMap { _.execId }
    val log = LogExchange.logger(loggerName, channelName, execId)
    val scope = task.scope
    // to change from global being the default to overriding, switch the order of state.get and data.get
    def getOr[T](key: AttributeKey[T], default: T): T =
      data.get(scope, key) orElse state.get(key) getOrElse default
    val screenLevel = getOr(logLevel.key, Level.Info)
    val backingLevel = getOr(persistLogLevel.key, Level.Debug)
    val screenTrace = getOr(traceLevel.key, defaultTraceLevel(state))
    val backingTrace = getOr(persistTraceLevel.key, Int.MaxValue)
    val extraBacked = state.globalLogging.backed :: relay :: Nil
    val consoleOpt = consoleLocally(state, console)
    val config = MainAppender.MainAppenderConfig(
      consoleOpt,
      backed,
      extraBacked ::: extra,
      screenLevel,
      backingLevel,
      screenTrace,
      backingTrace
    )
    multiLogger(log, config)
  }

  // Return None if the exec is not from console origin.
  def consoleLocally(state: State, console: Appender): Option[Appender] =
    state.currentCommand match {
      case Some(x: Exec) =>
        x.source match {
          // TODO: Fix this stringliness
          case Some(x: CommandSource) if x.channelName == "console0" => Option(console)
          case Some(_: CommandSource)                                => None
          case _                                                     => Option(console)
        }
      case _ => Option(console)
    }

  def defaultTraceLevel(state: State): Int =
    if (state.interactive) -1 else Int.MaxValue

  def suppressedMessage(
      key: ScopedKey[_],
      state: State
  ): SuppressedTraceContext => Option[String] = {
    lazy val display = Project.showContextKey(state)
    def commandBase = "last " + display.show(unwrapStreamsKey(key))
    def command(useFormat: Boolean) =
      if (useFormat) BLUE + commandBase + RESET else s"'$commandBase'"
    context =>
      Some("Stack trace suppressed: run %s for the full output.".format(command(context.useFormat)))
  }

  def unwrapStreamsKey(key: ScopedKey[_]): ScopedKey[_] = key.scope.task match {
    case Select(task) => ScopedKey(key.scope.copy(task = Zero), task)
    case _            => key // should never get here
  }

  def backgroundLog(
      data: Settings[Scope],
      state: State,
      task: ScopedKey[_],
      console: Appender,
      /* TODO: backed: Appender,*/
      relay: Appender,
      extra: List[Appender]
  ): ManagedLogger = {
    val execOpt = state.currentCommand
    val loggerName: String = s"bg-${task.key.label}-${generateId.incrementAndGet}"
    val channelName: Option[String] = execOpt flatMap (_.source map (_.channelName))
    // val execId: Option[String] = execOpt flatMap { _.execId }
    val log = LogExchange.logger(loggerName, channelName, None)
    LogExchange.unbindLoggerAppenders(loggerName)
    val consoleOpt = consoleLocally(state, console)
    LogExchange.bindLoggerAppenders(
      loggerName,
      (consoleOpt.toList map { _ -> Level.Debug }) ::: (relay -> Level.Debug) :: Nil)
    log
  }

  // TODO: Fix this
  // if global logging levels are not explicitly set, set them from project settings
  // private[sbt] def setGlobalLogLevels(s: State, data: Settings[Scope]): State =
  //   if (hasExplicitGlobalLogLevels(s))
  //     s
  //   else {
  //     val logging = s.globalLogging
  //     def get[T](key: SettingKey[T]) = key in GlobalScope get data
  //     def transfer(l: AbstractLogger, traceKey: SettingKey[Int], levelKey: SettingKey[Level.Value]): Unit = {
  //       get(traceKey).foreach(l.setTrace)
  //       get(levelKey).foreach(l.setLevel)
  //     }
  //     logging.full match {
  //       case a: AbstractLogger => transfer(a, traceLevel, logLevel)
  //       case _                 => ()
  //     }
  //     transfer(logging.backed, persistTraceLevel, persistLogLevel)
  //     s
  //   }

  def setGlobalLogLevel(s: State, level: Level.Value): State =
    s.put(BasicKeys.explicitGlobalLogLevels, true).put(Keys.logLevel.key, level)

  // This is the default implementation for the relay appender
  val defaultRelay: Unit => Appender = _ => defaultRelayImpl

  private[this] lazy val defaultRelayImpl: RelayAppender = {
    val appender = new RelayAppender("Relay0")
    appender.start()
    appender
  }

  private[sbt] def settingsLogger(state: State): Def.Setting[_] =
    // strict to avoid retaining a reference to `state`
    sLog in GlobalScope :== globalWrapper(state)

  // construct a Logger that delegates to the global logger, but only holds a weak reference
  //  this is an approximation to the ideal that would invalidate the delegate after loading completes
  private[this] def globalWrapper(s: State): Logger =
    new Logger {
      private[this] val ref = new java.lang.ref.WeakReference(s.globalLogging.full)
      private[this] def slog: Logger =
        Option(ref.get) getOrElse sys.error("Settings logger used after project was loaded.")

      override val ansiCodesSupported = slog.ansiCodesSupported
      override def trace(t: => Throwable) = slog.trace(t)
      override def success(message: => String) = slog.success(message)
      override def log(level: Level.Value, message: => String) = slog.log(level, message)
    }
}
