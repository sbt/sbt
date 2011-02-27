/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

	import java.io.PrintWriter
	import LogManager._
	import std.Transform
	import Project.ScopedKey
	import Keys.{LogLevel => ScreenLogLevel, PersistLogLevel}

object LogManager
{
	def construct(data: Settings[Scope]) = (task: ScopedKey[_], to: PrintWriter) =>
	{
		val scope = task.scope
		def level(key: AttributeKey[Level.Value], default: Level.Value): Level.Value = data.get(scope, key) getOrElse default
		val screenLevel = level(ScreenLogLevel.key, Level.Info)
		val backingLevel = level(PersistLogLevel.key, Level.Debug)

		val console = ConsoleLogger()
		val backed = ConsoleLogger(ConsoleLogger.printWriterOut(to), useColor = false) // TODO: wrap this with a filter that strips ANSI codes

		val multi = new MultiLogger(console :: backed :: Nil)
			// sets multi to the most verbose for clients that inspect the current level
		multi setLevel Level.union(backingLevel, screenLevel)
			// set the specific levels
		console setLevel screenLevel
		backed setLevel backingLevel
		multi: Logger
	}
}