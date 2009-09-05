/* sbt -- Simple Build Tool
 * Copyright 2008, 2009 Mark Harrah
 */
 package xsbt

object ConsoleLogger
{
	private val formatEnabled = ansiSupported && !formatExplicitlyDisabled

	private[this] def formatExplicitlyDisabled = java.lang.Boolean.getBoolean("sbt.log.noformat")
	private[this] def ansiSupported =
		try { jline.Terminal.getTerminal.isANSISupported }
		catch { case e: Exception => !isWindows }

	private[this] def os = System.getProperty("os.name")
	private[this] def isWindows = os.toLowerCase.indexOf("windows") >= 0
}

/** A logger that logs to the console.  On supported systems, the level labels are
* colored. */
class ConsoleLogger extends BasicLogger
{
	import ConsoleLogger.formatEnabled
	def messageColor(level: Level.Value) = Console.RESET
	def labelColor(level: Level.Value) =
		level match
		{
			case Level.Error => Console.RED
			case Level.Warn => Console.YELLOW
			case _ => Console.RESET
		}
	def successLabelColor = Console.GREEN
	def successMessageColor = Console.RESET
	override def success(message: => String)
	{
		if(atLevel(Level.Info))
			log(successLabelColor, Level.SuccessLabel, successMessageColor, message)
	}
	def trace(t: => Throwable): Unit =
		System.out.synchronized
		{
			if(traceEnabled)
				t.printStackTrace
		}
	def log(level: Level.Value, message: => String)
	{
		if(atLevel(level))
			log(labelColor(level), level.toString, messageColor(level), message)
	}
	private def setColor(color: String)
	{
		if(formatEnabled)
			System.out.synchronized { System.out.print(color) }
	}
	private def log(labelColor: String, label: String, messageColor: String, message: String): Unit =
		System.out.synchronized
		{
			for(line <- message.split("""\n"""))
			{
				setColor(Console.RESET)
				System.out.print('[')
				setColor(labelColor)
				System.out.print(label)
				setColor(Console.RESET)
				System.out.print("] ")
				setColor(messageColor)
				System.out.print(line)
				setColor(Console.RESET)
				System.out.println()
			}
		}

	def logAll(events: Seq[LogEvent]) = System.out.synchronized { events.foreach(log) }
	def control(event: ControlEvent.Value, message: => String)
		{ log(labelColor(Level.Info), Level.Info.toString, Console.BLUE, message) }
}