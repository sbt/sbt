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

	def defaultScreen: AbstractLogger = ConsoleLogger()
	
	def defaultBacked(useColor: Boolean = ConsoleLogger.formatEnabled): PrintWriter => ConsoleLogger =
		to => ConsoleLogger(ConsoleLogger.printWriterOut(to), useColor = useColor) // TODO: should probably filter ANSI codes when useColor=false

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
		val extraBacked = (state get Keys.globalLogging).map(_.backed).toList
		multiLogger( new MultiLoggerConfig(console, backed, extraBacked ::: extra, screenLevel, backingLevel, screenTrace, backingTrace) )
	}
	def multiLogger(config: MultiLoggerConfig): Logger =
	{
			import config._
		val multi = new MultiLogger(console :: backed :: extra)
			// sets multi to the most verbose for clients that inspect the current level
		multi setLevel Level.unionAll(backingLevel :: screenLevel :: extra.map(_.getLevel))
			// set the specific levels
		console setLevel screenLevel
		backed setLevel backingLevel
		console setTrace screenTrace
		backed setTrace backingTrace
		multi: Logger
	}
	def globalDefault(writer: PrintWriter, backing: GlobalLogBacking): GlobalLogging =
	{
		val backed = defaultBacked()(writer)
		val full = multiLogger(defaultMultiConfig( backed ) )
		GlobalLogging(full, backed, backing)
	}

	def defaultMultiConfig(backing: AbstractLogger): MultiLoggerConfig =
		new MultiLoggerConfig(defaultScreen, backing, Nil, Level.Info, Level.Debug, -1, Int.MaxValue)
}
final case class MultiLoggerConfig(console: AbstractLogger, backed: AbstractLogger, extra: List[AbstractLogger], screenLevel: Level.Value, backingLevel: Level.Value, screenTrace: Int, backingTrace: Int)
trait LogManager
{
	def apply(data: Settings[Scope], state: State, task: ScopedKey[_], writer: PrintWriter): Logger
}
final case class GlobalLogBacking(file: File, last: Option[File])
{
	def shift(newFile: File) = GlobalLogBacking(newFile, Some(file))
	def unshift = GlobalLogBacking(last getOrElse file, None)
}
final case class GlobalLogging(full: Logger, backed: ConsoleLogger, backing: GlobalLogBacking)
