package sbt

	import java.io.PrintWriter

object MainLogging
{
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

	def globalDefault(console: ConsoleOut): (PrintWriter, GlobalLogBacking) => GlobalLogging =
	{
		lazy val f: (PrintWriter, GlobalLogBacking) => GlobalLogging = (writer, backing) => {
			val backed = defaultBacked()(writer)
			val full = multiLogger(defaultMultiConfig(console, backed ) )
			GlobalLogging(full, console, backed, backing, f)
		}
		f
	}

	@deprecated("Explicitly specify the console output.", "0.13.0")
	def defaultMultiConfig(backing: AbstractLogger): MultiLoggerConfig =
		defaultMultiConfig(ConsoleOut.systemOut, backing)
	def defaultMultiConfig(console: ConsoleOut, backing: AbstractLogger): MultiLoggerConfig =
		new MultiLoggerConfig(defaultScreen(console, ConsoleLogger.noSuppressedMessage), backing, Nil, Level.Info, Level.Debug, -1, Int.MaxValue)

	@deprecated("Explicitly specify the console output.", "0.13.0")
	def defaultScreen(): AbstractLogger = ConsoleLogger()

	@deprecated("Explicitly specify the console output.", "0.13.0")
	def defaultScreen(suppressedMessage: SuppressedTraceContext => Option[String]): AbstractLogger = ConsoleLogger(suppressedMessage = suppressedMessage)

	def defaultScreen(console: ConsoleOut): AbstractLogger = ConsoleLogger(console)
	def defaultScreen(console: ConsoleOut, suppressedMessage: SuppressedTraceContext => Option[String]): AbstractLogger =
		ConsoleLogger(console, suppressedMessage = suppressedMessage)
	
	def defaultBacked(useColor: Boolean = ConsoleLogger.formatEnabled): PrintWriter => ConsoleLogger =
		to => ConsoleLogger(ConsoleOut.printWriterOut(to), useColor = useColor)
}

final case class MultiLoggerConfig(console: AbstractLogger, backed: AbstractLogger, extra: List[AbstractLogger],
	screenLevel: Level.Value, backingLevel: Level.Value, screenTrace: Int, backingTrace: Int)