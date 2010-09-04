/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010 Mark Harrah
 */
package sbt

	import java.io.{PrintStream, PrintWriter}

object ConsoleLogger
{
	def systemOut: ConsoleOut = printStreamOut(System.out)
	def printStreamOut(out: PrintStream): ConsoleOut = new ConsoleOut {
		val lockObject = out
		def print(s: String) = out.print(s)
		def println(s: String) = out.println(s)
		def println() = out.println()
	}
	def printWriterOut(out: PrintWriter): ConsoleOut = new ConsoleOut {
		val lockObject = out
		def print(s: String) = out.print(s)
		def println(s: String) = out.println(s)
		def println() = out.println()
	}

	private val formatEnabled = ansiSupported && !formatExplicitlyDisabled

	private[this] def formatExplicitlyDisabled = java.lang.Boolean.getBoolean("sbt.log.noformat")
	private[this] def ansiSupported =
		try { jline.Terminal.getTerminal.isANSISupported }
		catch { case e: Exception => !isWindows }

	private[this] def os = System.getProperty("os.name")
	private[this] def isWindows = os.toLowerCase.indexOf("windows") >= 0
	
	def apply(): ConsoleLogger = apply(systemOut)
	def apply(out: PrintStream): ConsoleLogger = apply(printStreamOut(out))
	def apply(out: PrintWriter): ConsoleLogger = apply(printWriterOut(out))
	def apply(out: ConsoleOut, ansiCodesSupported: Boolean = formatEnabled, useColor: Boolean = true): ConsoleLogger =
		new ConsoleLogger(out, ansiCodesSupported, useColor)
}

/** A logger that logs to the console.  On supported systems, the level labels are
* colored.
*
* This logger is not thread-safe.*/
class ConsoleLogger private[ConsoleLogger](val out: ConsoleOut, override val ansiCodesSupported: Boolean, val useColor: Boolean) extends BasicLogger
{
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
		out.lockObject.synchronized
		{
			val traceLevel = getTrace
			if(traceLevel >= 0)
				out.print(StackTrace.trimmed(t, traceLevel))
		}
	def log(level: Level.Value, message: => String)
	{
		if(atLevel(level))
			log(labelColor(level), level.toString, messageColor(level), message)
	}
	private def reset(): Unit = setColor(Console.RESET)
	
	private def setColor(color: String)
	{
		if(ansiCodesSupported && useColor)
			out.lockObject.synchronized { out.print(color) }
	}
	private def log(labelColor: String, label: String, messageColor: String, message: String): Unit =
		out.lockObject.synchronized
		{
			for(line <- message.split("""\n"""))
			{
				reset()
				out.print("[")
				setColor(labelColor)
				out.print(label)
				reset()
				out.print("] ")
				setColor(messageColor)
				out.print(line)
				reset()
				out.println()
			}
		}

	def logAll(events: Seq[LogEvent]) = out.lockObject.synchronized { events.foreach(log) }
	def control(event: ControlEvent.Value, message: => String)
		{ log(labelColor(Level.Info), Level.Info.toString, Console.BLUE, message) }
}
sealed trait ConsoleOut
{
	val lockObject: AnyRef
	def print(s: String): Unit
	def println(s: String): Unit
	def println(): Unit
}