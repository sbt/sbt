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

object LogManager
{
	def construct(data: Settings[Scope], state: State) = (task: ScopedKey[_], to: PrintWriter) =>
	{
		val manager = logManager in task.scope get data getOrElse default
		manager(data, state, task, to)
	}
	lazy val default: LogManager = withLoggers()
	def defaults(extra: ScopedKey[_] => Seq[AbstractLogger]): LogManager  =  withLoggers(extra = extra)

	def withScreenLogger(mk: => AbstractLogger): LogManager = withLoggers(mk)
	
	def withLoggers(screen: => AbstractLogger = defaultScreen, backed: PrintWriter => AbstractLogger = defaultBacked(), extra: ScopedKey[_] => Seq[AbstractLogger] = _ => Nil): LogManager =
		new LogManager {
			def apply(data: Settings[Scope], state: State, task: ScopedKey[_], to: PrintWriter): Logger =
				defaultLogger(data, state, task, screen, backed(to), extra(task).toList)
		}

	def defaultLogger(data: Settings[Scope], state: State, task: ScopedKey[_], console: AbstractLogger, backed: AbstractLogger, extra: List[AbstractLogger]): Logger =
	{
		val scope = task.scope
		def getOr[T](key: AttributeKey[T], default: T): T = data.get(scope, key) getOrElse default
		val screenLevel = getOr(logLevel.key, Level.Info)
		val backingLevel = getOr(persistLogLevel.key, Level.Debug)
		val screenTrace = getOr(traceLevel.key, -1)
		val backingTrace = getOr(persistTraceLevel.key, Int.MaxValue)
		val extraBacked = state.globalLogging.backed :: Nil
		multiLogger( new MultiLoggerConfig(console, backed, extraBacked ::: extra, screenLevel, backingLevel, screenTrace, backingTrace) )
	}
}

trait LogManager
{
	def apply(data: Settings[Scope], state: State, task: ScopedKey[_], writer: PrintWriter): Logger
}
