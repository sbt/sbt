/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.io.PrintWriter

import sbt.Def.ScopedKey
import sbt.Keys._
import sbt.Scope.GlobalScope
import sbt.internal.util.MainAppender._
import sbt.internal.util.{ Terminal => ITerminal, _ }
import sbt.util.{ Level, LogExchange, Logger, LoggerContext }
import org.apache.logging.log4j.core.{ Appender => XAppender }

sealed abstract class LogManager {
  def apply(
      data: Settings[Scope],
      state: State,
      task: ScopedKey[_],
      writer: PrintWriter,
      context: LoggerContext,
  ): ManagedLogger
  @deprecated("Use alternate apply that provides a LoggerContext", "1.4.0")
  def apply(
      data: Settings[Scope],
      state: State,
      task: ScopedKey[_],
      writer: PrintWriter
  ): ManagedLogger = apply(data, state, task, writer, LoggerContext.globalContext)

  def backgroundLog(
      data: Settings[Scope],
      state: State,
      task: ScopedKey[_],
      context: LoggerContext
  ): ManagedLogger
  @deprecated("Use alternate background log that provides a LoggerContext", "1.4.0")
  final def backgroundLog(data: Settings[Scope], state: State, task: ScopedKey[_]): ManagedLogger =
    backgroundLog(data, state, task, LoggerContext.globalContext)
}

/**
 * A functional interface that allows us to preserve binary compatibility
 * for LogManager.defaults with the old log4j variant.
 */
trait AppenderSupplier {
  def apply(s: ScopedKey[_]): Seq[Appender]
}

object LogManager {
  import java.util.concurrent.atomic.AtomicInteger
  private val generateId: AtomicInteger = new AtomicInteger

  // This is called by mkStreams
  //
  def construct(
      data: Settings[Scope],
      state: State
  ): (ScopedKey[_], PrintWriter) => ManagedLogger =
    (task: ScopedKey[_], to: PrintWriter) => {
      val context = state.get(Keys.loggerContext).getOrElse(LoggerContext.globalContext)
      val manager: LogManager =
        (logManager in task.scope).get(data) getOrElse defaultManager(state.globalLogging.console)
      manager(data, state, task, to, context)
    }

  def constructBackgroundLog(
      data: Settings[Scope],
      state: State
  ): (ScopedKey[_]) => ManagedLogger =
    (task: ScopedKey[_]) => {
      val manager: LogManager =
        (logManager in task.scope).get(data) getOrElse {
          val term = state.get(Keys.taskTerminal).getOrElse(ITerminal.get)
          defaultManager(ConsoleOut.terminalOut(term))
        }
      val context = state.get(Keys.loggerContext).getOrElse(LoggerContext.globalContext)
      manager.backgroundLog(data, state, task, context)
    }

  def defaultManager(console: ConsoleOut): LogManager =
    withLoggers((_, _) => defaultScreen(console))

  @deprecated(
    "use defaults that takes AppenderSupplier instead of ScopedKey[_] => Seq[Appender]",
    "1.4.0"
  )
  def defaults(extra: ScopedKey[_] => Seq[XAppender], console: ConsoleOut): LogManager =
    defaults((sk: ScopedKey[_]) => extra(sk).map(new ConsoleAppenderFromLog4J("extra", _)), console)

  @deprecated("use single argument version", "1.4.3")
  def defaults(extra: AppenderSupplier, console: ConsoleOut): LogManager =
    withLoggers(
      (task, state) => defaultScreen(console, suppressedMessage(task, state)),
      extra = extra
    )
  // This is called by Defaults.
  def defaults(extra: AppenderSupplier): LogManager =
    withLoggers(
      (task, state) => {
        val term = state.get(Keys.taskTerminal).getOrElse(ITerminal.get)
        defaultScreen(ConsoleOut.terminalOut(term), suppressedMessage(task, state)),
      },
      extra = extra
    )

  def withScreenLogger(mk: (ScopedKey[_], State) => Appender): LogManager =
    withLoggers(screen = mk)

  def withLoggers(
      screen: (ScopedKey[_], State) => Appender = (_, s) => defaultScreen(s.globalLogging.console),
      backed: PrintWriter => Appender = defaultBacked,
      relay: Unit => Appender = defaultRelay,
      extra: AppenderSupplier = _ => Nil
  ): LogManager = new DefaultLogManager(screen, backed, relay, extra)

  private class DefaultLogManager(
      screen: (ScopedKey[_], State) => Appender,
      backed: PrintWriter => Appender,
      relay: Unit => Appender,
      extra: AppenderSupplier
  ) extends LogManager {
    def apply(
        data: Settings[Scope],
        state: State,
        task: ScopedKey[_],
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
        data: Settings[Scope],
        state: State,
        task: ScopedKey[_],
        context: LoggerContext
    ): ManagedLogger = {
      val console = screen(task, state)
      LogManager.backgroundLog(data, state, task, console, relay(()), context)
    }
  }

  // to change from global being the default to overriding, switch the order of state.get and data.get
  def getOr[T](
      key: AttributeKey[T],
      data: Settings[Scope],
      scope: Scope,
      state: State,
      default: T
  ): T =
    data.get(scope, key) orElse state.get(key) getOrElse default

  @deprecated("Use defaultLogger that provides a LoggerContext", "1.4.0")
  def defaultLogger(
      data: Settings[Scope],
      state: State,
      task: ScopedKey[_],
      console: Appender,
      backed: Appender,
      relay: Appender,
      extra: List[Appender]
  ): ManagedLogger =
    defaultLogger(data, state, task, console, backed, relay, extra, LoggerContext.globalContext)
  // This is the main function that is used to generate the logger for tasks.
  def defaultLogger(
      data: Settings[Scope],
      state: State,
      task: ScopedKey[_],
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
      key: ScopedKey[_],
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
    val consoleOpt = consoleLocally(state, console) map {
      case a: Appender =>
        a.setTrace(screenTrace)
        a
      case a => a
    }
    consoleOpt.foreach(a => context.addAppender(loggerName, a -> screenLevel))
    context.addAppender(loggerName, relay -> backingLevel)
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

  def setGlobalLogLevel(s: State, level: Level.Value): State = {
    val s1 = s.put(BasicKeys.explicitGlobalLogLevels, true).put(Keys.logLevel.key, level)
    val gl = s1.globalLogging
    LoggerContext.globalContext.clearAppenders(gl.full.name)
    LoggerContext.globalContext.addAppender(gl.full.name, gl.backed -> level)
    s1
  }

  @deprecated("No longer used.", "1.4.0")
  private[sbt] def progressLogger(appender: ConsoleAppender): ManagedLogger = {
    val log = LogExchange.logger("progress", None, None)
    LoggerContext.globalContext.clearAppenders("progress")
    LoggerContext.globalContext.addAppender("progress", appender -> Level.Info)
    log
  }

  // This is the default implementation for the relay appender
  val defaultRelay: Unit => ConsoleAppender = _ => defaultRelayImpl

  private[this] lazy val defaultRelayImpl: ConsoleAppender = new RelayAppender("Relay0")

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

      override val ansiCodesSupported = ITerminal.isAnsiSupported
      override def trace(t: => Throwable) = slog.trace(t)
      override def success(message: => String) = slog.success(message)
      override def log(level: Level.Value, message: => String) = slog.log(level, message)
    }
}
