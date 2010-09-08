/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

	import java.io.PrintWriter
	import LogManager._
	import std.Transform

object LogManager
{
	def construct(context: Transform.Context[Project], settings: Settings) = (task: Task[_], to: PrintWriter) =>
	{
		val owner = context owner task
		val ownerName = owner flatMap ( context ownerName _ ) getOrElse ""
		val taskPath = (context staticName task).toList ::: ownerName  :: Nil
		def level(key: AttributeKey[Level.Value], default: Level.Value): Level.Value = settings.get(key, taskPath) getOrElse default
		val screenLevel = level(ScreenLogLevel, Level.Info)
		val backingLevel = level(PersistLogLevel, Level.Debug)

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

	val ScreenLogLevel = AttributeKey[Level.Value]("screen log level")
	val PersistLogLevel = AttributeKey[Level.Value]("persist log level")
}