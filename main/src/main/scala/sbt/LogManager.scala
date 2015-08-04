/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

import java.io.PrintWriter
import java.io.File
import LogManager._
import std.Transform
import Def.ScopedKey
import Scope.GlobalScope
import MainLogging._
import BasicKeys.explicitGlobalLogLevels
import Keys.{ logLevel, logManager, persistLogLevel, persistTraceLevel, sLog, state, traceLevel }
import scala.Console.{ BLUE, RESET }

object LogManager {
  def construct(data: Settings[Scope], state: State) = (task: ScopedKey[_], to: PrintWriter) =>
    {
      val manager = logManager in task.scope get data getOrElse defaultManager(state.globalLogging.console)
      manager(data, state, task, to)
    }
  @deprecated("Use defaultManager to explicitly specify standard out.", "0.13.0")
  lazy val default: LogManager = defaultManager(StandardMain.console)

  def defaultManager(console: ConsoleOut): LogManager = withLoggers((sk, s) => defaultScreen(console))

  @deprecated("Explicitly specify standard out.", "0.13.0")
  def defaults(extra: ScopedKey[_] => Seq[AbstractLogger]): LogManager = defaults(extra, StandardMain.console)

  def defaults(extra: ScopedKey[_] => Seq[AbstractLogger], console: ConsoleOut): LogManager =
    withLoggers((task, state) => defaultScreen(console, suppressedMessage(task, state)), extra = extra)

  def withScreenLogger(mk: (ScopedKey[_], State) => AbstractLogger): LogManager = withLoggers(screen = mk)

  def withLoggers(screen: (ScopedKey[_], State) => AbstractLogger = (sk, s) => defaultScreen(s.globalLogging.console),
    backed: PrintWriter => AbstractLogger = defaultBacked(),
    extra: ScopedKey[_] => Seq[AbstractLogger] = _ => Nil): LogManager = new LogManager {
    def apply(data: Settings[Scope], state: State, task: ScopedKey[_], to: PrintWriter): Logger =
      defaultLogger(data, state, task, screen(task, state), backed(to), extra(task).toList)
  }

  def defaultLogger(data: Settings[Scope], state: State, task: ScopedKey[_], console: AbstractLogger, backed: AbstractLogger, extra: List[AbstractLogger]): Logger =
    {
      val scope = task.scope
      // to change from global being the default to overriding, switch the order of state.get and data.get
      def getOr[T](key: AttributeKey[T], default: T): T = data.get(scope, key) orElse state.get(key) getOrElse default
      val screenLevel = getOr(logLevel.key, Level.Info)
      val backingLevel = getOr(persistLogLevel.key, Level.Debug)
      val screenTrace = getOr(traceLevel.key, defaultTraceLevel(state))
      val backingTrace = getOr(persistTraceLevel.key, Int.MaxValue)
      val extraBacked = state.globalLogging.backed :: Nil
      multiLogger(new MultiLoggerConfig(console, backed, extraBacked ::: extra, screenLevel, backingLevel, screenTrace, backingTrace))
    }
  def defaultTraceLevel(state: State): Int =
    if (state.interactive) -1 else Int.MaxValue
  def suppressedMessage(key: ScopedKey[_], state: State): SuppressedTraceContext => Option[String] =
    {
      lazy val display = Project.showContextKey(state)
      def commandBase = "last " + display(unwrapStreamsKey(key))
      def command(useColor: Boolean) = if (useColor) BLUE + commandBase + RESET else "'" + commandBase + "'"
      context => Some("Stack trace suppressed: run %s for the full output.".format(command(context.useColor)))
    }
  def unwrapStreamsKey(key: ScopedKey[_]): ScopedKey[_] = key.scope.task match {
    case Select(task) => ScopedKey(key.scope.copy(task = Global), task)
    case _            => key // should never get here
  }

  // if global logging levels are not explicitly set, set them from project settings
  private[sbt] def setGlobalLogLevels(s: State, data: Settings[Scope]): State =
    if (hasExplicitGlobalLogLevels(s))
      s
    else {
      val logging = s.globalLogging
      def get[T](key: SettingKey[T]) = key in GlobalScope get data
      def transfer(l: AbstractLogger, traceKey: SettingKey[Int], levelKey: SettingKey[Level.Value]): Unit = {
        get(traceKey).foreach(l.setTrace)
        get(levelKey).foreach(l.setLevel)
      }
      logging.full match {
        case a: AbstractLogger => transfer(a, traceLevel, logLevel)
        case _                 => ()
      }
      transfer(logging.backed, persistTraceLevel, persistLogLevel)
      s
    }

  def setGlobalLogLevel(s: State, level: Level.Value): State = {
    s.globalLogging.full match {
      case a: AbstractLogger => a.setLevel(level)
      case _                 => ()
    }
    s.put(BasicKeys.explicitGlobalLogLevels, true).put(Keys.logLevel.key, level)
  }

  private[this] def setExplicitGlobalLogLevels(s: State, flag: Boolean): State =
    s.put(BasicKeys.explicitGlobalLogLevels, flag)
  private[this] def hasExplicitGlobalLogLevels(s: State): Boolean =
    State.getBoolean(s, BasicKeys.explicitGlobalLogLevels, default = false)

  private[sbt] def settingsLogger(state: State): Def.Setting[_] =
    // strict to avoid retaining a reference to `state`
    sLog in GlobalScope :== globalWrapper(state)

  // construct a Logger that delegates to the global logger, but only holds a weak reference
  //  this is an approximation to the ideal that would invalidate the delegate after loading completes
  private[this] def globalWrapper(s: State): Logger = {
    new Logger {
      private[this] val ref = new java.lang.ref.WeakReference(s.globalLogging.full)
      private[this] def slog: Logger = Option(ref.get) getOrElse sys.error("Settings logger used after project was loaded.")

      override val ansiCodesSupported = slog.ansiCodesSupported
      override def trace(t: => Throwable) = slog.trace(t)
      override def success(message: => String) = slog.success(message)
      override def log(level: Level.Value, message: => String) = slog.log(level, message)
    }
  }
}

trait LogManager {
  def apply(data: Settings[Scope], state: State, task: ScopedKey[_], writer: PrintWriter): Logger
}
