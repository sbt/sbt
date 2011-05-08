/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

	import java.io.PrintWriter
	import LogManager._
	import std.Transform
	import Project.ScopedKey
	import Keys.{logLevel, logManager, persistLogLevel, persistTraceLevel, traceLevel}

object LogManager
{
	def construct(data: Settings[Scope]) = (task: ScopedKey[_], to: PrintWriter) =>
	{
		val manager = logManager in task.scope get data getOrElse default
		manager(data, task, to)
	}
	lazy val default: LogManager = withLoggers()

	def defaultScreen: AbstractLogger = ConsoleLogger()
	def defaultBacked(useColor: Boolean): PrintWriter => AbstractLogger =
		to => ConsoleLogger(ConsoleLogger.printWriterOut(to), useColor = useColor) // TODO: should probably filter ANSI codes when useColor=false

	def withScreenLogger(mk: => AbstractLogger): LogManager = withLoggers(mk)
	
	def withLoggers(screen: => AbstractLogger = defaultScreen, backed: PrintWriter => AbstractLogger = defaultBacked(true)): LogManager =
		new LogManager {
			def apply(data: Settings[Scope], task: ScopedKey[_], to: PrintWriter): Logger =
				defaultLogger(data, task, screen, backed(to))
		}

	def defaultLogger(data: Settings[Scope], task: ScopedKey[_], console: AbstractLogger, backed: AbstractLogger): Logger =
	{
		val scope = task.scope
		def getOr[T](key: AttributeKey[T], default: T): T = data.get(scope, key) getOrElse default
		val screenLevel = getOr(logLevel.key, Level.Info)
		val backingLevel = getOr(persistLogLevel.key, Level.Debug)
		val screenTrace = getOr(traceLevel.key, -1)
		val backingTrace = getOr(persistTraceLevel.key, Int.MaxValue)

		val multi = new MultiLogger(console :: backed :: Nil)
			// sets multi to the most verbose for clients that inspect the current level
		multi setLevel Level.union(backingLevel, screenLevel)
			// set the specific levels
		console setLevel screenLevel
		backed setLevel backingLevel
		console setTrace screenTrace
		backed setTrace backingTrace
		multi: Logger
	}
}
trait LogManager
{
	def apply(data: Settings[Scope], task: ScopedKey[_], writer: PrintWriter): Logger
}