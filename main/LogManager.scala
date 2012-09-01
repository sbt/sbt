/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

	import java.io.PrintWriter
	import java.io.File
	import LogManager._
	import std.Transform
	import Project.ScopedKey
	import Scope.GlobalScope
	import MainLogging._
	import Keys.{logLevel, logManager, persistLogLevel, persistTraceLevel, state, traceLevel}
	import scala.Console.{BLUE,RESET}

object LogManager
{
	def construct(data: Settings[Scope], state: State) = (task: ScopedKey[_], to: PrintWriter) =>
	{
		val manager = logManager in task.scope get data getOrElse defaultManager(StandardMain.console)
		manager(data, state, task, to)
	}
	@deprecated("Use defaultManager to explicitly specify standard out.", "0.13.0")
	lazy val default: LogManager = defaultManager(StandardMain.console)

	def defaultManager(console: ConsoleOut): LogManager = withLoggers((sk,s) => defaultScreen(console))

	@deprecated("Explicitly specify standard out.", "0.13.0")
	def defaults(extra: ScopedKey[_] => Seq[AbstractLogger]): LogManager = defaults(extra, StandardMain.console)

	def defaults(extra: ScopedKey[_] => Seq[AbstractLogger], console: ConsoleOut): LogManager  =
		withLoggers((task,state) => defaultScreen(console, suppressedMessage(task, state)), extra = extra)

	def withScreenLogger(mk: (ScopedKey[_], State) => AbstractLogger): LogManager = withLoggers(screen = mk)
	
	def withLoggers(screen: (ScopedKey[_], State) => AbstractLogger = (sk, s) => defaultScreen(StandardMain.console),
		backed: PrintWriter => AbstractLogger = defaultBacked(),
		extra: ScopedKey[_] => Seq[AbstractLogger] = _ => Nil): LogManager = new LogManager {
			def apply(data: Settings[Scope], state: State, task: ScopedKey[_], to: PrintWriter): Logger =
				defaultLogger(data, state, task, screen(task, state), backed(to), extra(task).toList)
		}

	def defaultLogger(data: Settings[Scope], state: State, task: ScopedKey[_], console: AbstractLogger, backed: AbstractLogger, extra: List[AbstractLogger]): Logger =
	{
		val scope = task.scope
		def getOr[T](key: AttributeKey[T], default: T): T = data.get(scope, key) getOrElse default
		val screenLevel = getOr(logLevel.key, Level.Info)
		val backingLevel = getOr(persistLogLevel.key, Level.Debug)
		val screenTrace = getOr(traceLevel.key, defaultTraceLevel(state))
		val backingTrace = getOr(persistTraceLevel.key, Int.MaxValue)
		val extraBacked = state.globalLogging.backed :: Nil
		multiLogger( new MultiLoggerConfig(console, backed, extraBacked ::: extra, screenLevel, backingLevel, screenTrace, backingTrace) )
	}
	def defaultTraceLevel(state: State): Int =
		if(state.interactive) -1 else Int.MaxValue
	def suppressedMessage(key: ScopedKey[_], state: State): SuppressedTraceContext => Option[String] =
	{
		lazy val display = Project.showContextKey(state)
		def commandBase = "last " + display(unwrapStreamsKey(key))
		def command(useColor: Boolean) = if(useColor) BLUE + commandBase + RESET else "'" + commandBase + "'"
		context => Some( "Stack trace suppressed: run %s for the full output.".format(command(context.useColor)))
	}
	def unwrapStreamsKey(key: ScopedKey[_]): ScopedKey[_] = key.scope.task match {
		case Select(task) => ScopedKey(key.scope.copy(task = Global), task)
		case _ => key // should never get here
	}
}

trait LogManager
{
	def apply(data: Settings[Scope], state: State, task: ScopedKey[_], writer: PrintWriter): Logger
}
