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
	def globalDefault(writer: PrintWriter, backing: GlobalLogBacking): GlobalLogging =
	{
		val backed = defaultBacked()(writer)
		val full = multiLogger(defaultMultiConfig( backed ) )
		GlobalLogging(full, backed, backing)
	}

	def defaultMultiConfig(backing: AbstractLogger): MultiLoggerConfig =
		new MultiLoggerConfig(defaultScreen(ConsoleLogger.noSuppressedMessage), backing, Nil, Level.Info, Level.Debug, -1, Int.MaxValue)

	def defaultScreen(): AbstractLogger = ConsoleLogger()
	def defaultScreen(suppressedMessage: SuppressedTraceContext => Option[String]): AbstractLogger = ConsoleLogger(suppressedMessage = suppressedMessage)
	
	def defaultBacked(useColor: Boolean = ConsoleLogger.formatEnabled): PrintWriter => ConsoleLogger =
		to => ConsoleLogger(ConsoleLogger.printWriterOut(to), useColor = useColor)
}

final case class MultiLoggerConfig(console: AbstractLogger, backed: AbstractLogger, extra: List[AbstractLogger],
	screenLevel: Level.Value, backingLevel: Level.Value, screenTrace: Int, backingTrace: Int)