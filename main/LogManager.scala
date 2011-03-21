/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

	import java.io.PrintWriter
	import LogManager._
	import std.Transform
	import Project.ScopedKey
	import Keys.{logLevel, persistLogLevel, persistTraceLevel, traceLevel}

object LogManager
{
	def construct(data: Settings[Scope]) = (task: ScopedKey[_], to: PrintWriter) =>
	{
		val scope = task.scope
		def getOr[T](key: AttributeKey[T], default: T): T = data.get(scope, key) getOrElse default
		val screenLevel = getOr(logLevel.key, Level.Info)
		val backingLevel = getOr(persistLogLevel.key, Level.Debug)
		val screenTrace = getOr(traceLevel.key, -1)
		val backingTrace = getOr(persistTraceLevel.key, Int.MaxValue)

		val console = ConsoleLogger()
		val backed = ConsoleLogger(ConsoleLogger.printWriterOut(to), useColor = false) // TODO: wrap this with a filter that strips ANSI codes

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