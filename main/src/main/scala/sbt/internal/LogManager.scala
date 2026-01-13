/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import sbt.Def.ScopedKey
import sbt.Keys.*
import sbt.ProjectExtra.showContextKey
import sbt.Scope.Global
import sbt.ScopeAxis.{ Select, Zero }
import sbt.internal.util.MainAppender.*
import sbt.internal.util.{ Terminal as ITerminal, * }
import sbt.util.{ Level, Logger, LoggerContext }

import java.io.PrintWriter

sealed abstract class LogManager {
  def apply(
      data: Def.Settings,
      state: State,
      task: ScopedKey[?],
      writer: PrintWriter,
      context: LoggerContext,
  ): ManagedLogger
  def backgroundLog(
      data: Def.Settings,
      state: State,
      task: ScopedKey[?],
      context: LoggerContext
  ): ManagedLogger
}

/**
 * A functional interface that allows us to preserve binary compatibility
 * for LogManager.defaults with the old log4j variant.
 */
trait AppenderSupplier {
  def apply(s: ScopedKey[?]): Seq[Appender]
}

object LogManager {
  import java.util.concurrent.atomic.AtomicInteger
  private val generateId: AtomicInteger = new AtomicInteger

  // This is called by mkStreams
  //
  def construct(
      data: Def.Settings,
      state: State
  ): (ScopedKey[?], PrintWriter) => ManagedLogger =
    (task: ScopedKey[?], to: PrintWriter) => {
      val context = state.get(Keys.loggerContext).getOrElse(LoggerContext.globalContext)
      val manager: LogManager =
        (task.scope / logManager).get(data) getOrElse defaultManager(state.globalLogging.console)
      manager(data, state, task, to, context)
    }

  def constructBackgroundLog(
      data: Def.Settings,
      state: State,
      context: LoggerContext
  ): (ScopedKey[?]) => ManagedLogger =
    (task: ScopedKey[?]) => {
      val manager: LogManager =
        (task.scope / logManager).get(data) getOrElse defaultManager(state.globalLogging.console)
      manager.backgroundLog(data, state, task, context)
    }

  def defaultManager(console: ConsoleOut): LogManager =
    withLoggers((_, _) => defaultScreen(console))

  // This is called by Defaults.
  def defaults(extra: AppenderSupplier, console: ConsoleOut): LogManager =
    withLoggers(
      (task, state) => defaultScreen(console, suppressedMessage(task, state)),
      extra = extra
    )

  def withScreenLogger(mk: (ScopedKey[?], State) => Appender): LogManager =
    withLoggers(screen = mk)

  def withLoggers(
      screen: (ScopedKey[?], State) => Appender = (_, s) => defaultScreen(s.globalLogging.console),
      backed: PrintWriter => Appender = defaultBacked,
      relay: Unit => Appender = defaultRelay,
      extra: AppenderSupplier = _ => Nil
  ): LogManager = new DefaultLogManager(screen, backed, relay, extra)

  private class DefaultLogManager(
      screen: (ScopedKey[?], State) => Appender,
      backed: PrintWriter => Appender,
      relay: Unit => Appender,
      extra: AppenderSupplier
  ) extends LogManager {
    def apply(
        data: Def.Settings,
        state: State,
        task: ScopedKey[?],
        to: PrintWriter,
        context: LoggerContext,
    ): ManagedLogger =
      defaultLogger(
        data,
        state,
        task,
        screen(task, state),
        backed(to),
        relay(()),
        extra(task).toList,
        context,
      )

    def backgroundLog(
        data: Def.Settings,
        state: State,
        task: ScopedKey[?],
        context: LoggerContext
    ): ManagedLogger = {
      val console = ConsoleAppender.safe("bg-" + ConsoleAppender.generateName(), ITerminal.current)
      LogManager.backgroundLog(data, state, task, console, relay(()), context)
    }
  }

  // to change from global being the default to overriding, switch the order of state.get and data.get
  def getOr[T](
      key: AttributeKey[T],
      data: Def.Settings,
      scope: Scope,
      state: State,
      default: T
  ): T =
    data.get(ScopedKey(scope, key)).orElse(state.get(key)).getOrElse(default)

  // This is the main function that is used to generate the logger for tasks.
  def defaultLogger(
      data: Def.Settings,
      state: State,
      task: ScopedKey[?],
      console: Appender,
      backed: Appender,
      relay: Appender,
      extra: List[Appender],
      context: LoggerContext,
  ): ManagedLogger = {
    val execOpt = state.currentCommand
    val loggerName: String = s"${task.key.label}-${generateId.incrementAndGet}"
    val channelName: Option[String] = execOpt flatMap (_.source map (_.channelName))
    val execId: Option[String] = execOpt flatMap { _.execId }
    val log = context.logger(loggerName, channelName, execId)
    val scope = task.scope
    val screenLevel = getOr(logLevel.key, data, scope, state, Level.Info)
    val backingLevel = getOr(persistLogLevel.key, data, scope, state, Level.Debug)
    val screenTrace = getOr(traceLevel.key, data, scope, state, defaultTraceLevel(state))
    val backingTrace = getOr(persistTraceLevel.key, data, scope, state, Int.MaxValue)
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
    multiLogger(log, config, context)
  }

  // Return None if the exec is not from console origin.
  def consoleLocally(state: State, console: Appender): Option[Appender] =
    state.currentCommand match {
      case Some(x: Exec) =>
        x.source match {
          // TODO: Fix this stringliness
          case Some(x: CommandSource) if x.channelName == ConsoleChannel.defaultName =>
            Option(console)
          case _ => Option(console)
        }
      case _ => Option(console)
    }

  def defaultTraceLevel(state: State): Int =
    if (state.interactive) -1 else Int.MaxValue

  def suppressedMessage(
      key: ScopedKey[?],
      state: State
  ): SuppressedTraceContext => Option[String] = {
    val display = Project.showContextKey(state)
    def commandBase = "last " + display.show(unwrapStreamsKey(key))
    def command(useFormat: Boolean) =
      if (useFormat) s"${scala.Console.MAGENTA}$commandBase${scala.Console.RESET}"
      else s"'$commandBase'"

    { context =>
      Some(
        s"stack trace is suppressed; run ${command(context.useFormat)} for the full output"
      )
    }
  }

  def unwrapStreamsKey(key: ScopedKey[?]): ScopedKey[?] = key.scope.task match {
    case Select(task) => ScopedKey(key.scope.copy(task = Zero), task)
    case _            => key // should never get here
  }

  def backgroundLog(
      data: Def.Settings,
      state: State,
      task: ScopedKey[?],
      console: Appender,
      /* TODO: backed: Appender,*/
      relay: Appender,
      context: LoggerContext,
  ): ManagedLogger = {
    val scope = task.scope
    val screenLevel = getOr(logLevel.key, data, scope, state, Level.Info)
    val backingLevel = getOr(persistLogLevel.key, data, scope, state, Level.Debug)
    val screenTrace = getOr(traceLevel.key, data, scope, state, 0)
    val execOpt = state.currentCommand
    val loggerName: String = s"bg-${task.key.label}-${generateId.incrementAndGet}"
    val channelName: Option[String] = execOpt flatMap (_.source map (_.channelName))
    // val execId: Option[String] = execOpt flatMap { _.execId }
    val log = context.logger(loggerName, channelName, None)
    context.clearAppenders(loggerName)
    val consoleOpt = consoleLocally(state, console).map { a =>
      a.setTrace(screenTrace)
      a
    }
    consoleOpt.foreach(a => context.addAppender(loggerName, a -> screenLevel))
    context.addAppender(loggerName, relay -> backingLevel)
    log
  }

  // TODO: Fix this
  // if global logging levels are not explicitly set, set them from project settings
  // private[sbt] def setGlobalLogLevels(s: State, data: Def.Settings): State =
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

  def setGlobalLogLevel(s: State, level: Level.Value): State = {
    val s1 = s.put(BasicKeys.explicitGlobalLogLevels, true).put(Keys.logLevel.key, level)
    val gl = s1.globalLogging
    LoggerContext.globalContext.clearAppenders(gl.full.name)
    val consoleAppender = MainAppender.defaultScreen(gl.console)
    LoggerContext.globalContext.addAppender(gl.full.name, consoleAppender -> level)
    LoggerContext.globalContext.addAppender(gl.full.name, gl.backed -> level)
    s1
  }

  // This is the default implementation for the relay appender
  val defaultRelay: Unit => ConsoleAppender = _ => defaultRelayImpl

  private lazy val defaultRelayImpl: ConsoleAppender = new RelayAppender("Relay0")

  private[sbt] def settingsLogger(state: State): Def.Setting[?] =
    // strict to avoid retaining a reference to `state`
    Global / sLog :== globalWrapper(state)

  // construct a Logger that delegates to the global logger, but only holds a weak reference
  //  this is an approximation to the ideal that would invalidate the delegate after loading completes
  private def globalWrapper(s: State): Logger =
    new Logger {
      private val ref = new java.lang.ref.WeakReference(s.globalLogging.full)
      private def slog: Logger =
        Option(ref.get) getOrElse sys.error("Settings logger used after project was loaded.")

      val ansiCodesSupported = ITerminal.isAnsiSupported
      override def trace(t: => Throwable) = slog.trace(t)
      override def success(message: => String) = slog.success(message)
      override def log(level: Level.Value, message: => String) = slog.log(level, message)
    }
}
