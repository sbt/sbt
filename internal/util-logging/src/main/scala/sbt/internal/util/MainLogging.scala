package sbt.internal.util

import sbt.util._
import java.io.PrintWriter

object MainLogging {
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
        val full = multiLogger(defaultMultiConfig(console, backed))
        GlobalLogging(full, console, backed, backing, f)
      }
      f
    }

  def defaultMultiConfig(console: ConsoleOut, backing: AbstractLogger): MultiLoggerConfig =
    new MultiLoggerConfig(defaultScreen(console, ConsoleLogger.noSuppressedMessage), backing, Nil, Level.Info, Level.Debug, -1, Int.MaxValue)

  def defaultScreen(console: ConsoleOut): AbstractLogger = ConsoleLogger(console)
  def defaultScreen(console: ConsoleOut, suppressedMessage: SuppressedTraceContext => Option[String]): AbstractLogger =
    ConsoleLogger(console, suppressedMessage = suppressedMessage)

  def defaultBacked(useColor: Boolean = ConsoleLogger.formatEnabled): PrintWriter => ConsoleLogger =
    to => ConsoleLogger(ConsoleOut.printWriterOut(to), useColor = useColor)
}

final case class MultiLoggerConfig(console: AbstractLogger, backed: AbstractLogger, extra: List[AbstractLogger],
  screenLevel: Level.Value, backingLevel: Level.Value, screenTrace: Int, backingTrace: Int)
