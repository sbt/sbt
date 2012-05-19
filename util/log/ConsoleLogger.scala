/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010, 2011 Mark Harrah
 */
package sbt

	import java.io.{BufferedWriter, PrintStream, PrintWriter}

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
		def println(s: String) = { out.println(s); out.flush() }
		def println() = { out.println(); out.flush() }
	}
	def bufferedWriterOut(out: BufferedWriter): ConsoleOut = new ConsoleOut {
		val lockObject = out
		def print(s: String) = out.write(s)
		def println(s: String) = { out.write(s); println() }
		def println() = { out.newLine(); out.flush() }
	}

	val formatEnabled =
	{
		import java.lang.Boolean.{getBoolean, parseBoolean}
		val value = System.getProperty("sbt.log.format")
		if(value eq null) (ansiSupported && !getBoolean("sbt.log.noformat")) else parseBoolean(value)
	}

	private[this] def ansiSupported =
		try {
			val terminal = jline.Terminal.getTerminal
			terminal.enableEcho() // #460
			terminal.isANSISupported
		} catch {
			case e: Exception => !isWindows
		}
	val noSuppressedMessage = (_: SuppressedTraceContext) => None

	private[this] def os = System.getProperty("os.name")
	private[this] def isWindows = os.toLowerCase.indexOf("windows") >= 0
	
	def apply(out: PrintStream): ConsoleLogger = apply(printStreamOut(out))
	def apply(out: PrintWriter): ConsoleLogger = apply(printWriterOut(out))
	def apply(out: ConsoleOut = systemOut, ansiCodesSupported: Boolean = formatEnabled,
		useColor: Boolean = formatEnabled, suppressedMessage: SuppressedTraceContext => Option[String] = noSuppressedMessage): ConsoleLogger =
			new ConsoleLogger(out, ansiCodesSupported, useColor, suppressedMessage)

	private[this] val EscapeSequence = (27.toChar + "[^@-~]*[@-~]").r
	def stripEscapeSequences(s: String): String =
		EscapeSequence.pattern.matcher(s).replaceAll("")
}

/** A logger that logs to the console.  On supported systems, the level labels are
* colored.
*
* This logger is not thread-safe.*/
class ConsoleLogger private[ConsoleLogger](val out: ConsoleOut, override val ansiCodesSupported: Boolean, val useColor: Boolean, val suppressedMessage: SuppressedTraceContext => Option[String]) extends BasicLogger
{
		import scala.Console.{BLUE, GREEN, RED, RESET, YELLOW}
	def messageColor(level: Level.Value) = RESET
	def labelColor(level: Level.Value) =
		level match
		{
			case Level.Error => RED
			case Level.Warn => YELLOW
			case _ => RESET
		}
	def successLabelColor = GREEN
	def successMessageColor = RESET
	override def success(message: => String)
	{
		if(successEnabled)
			log(successLabelColor, Level.SuccessLabel, successMessageColor, message)
	}
	def trace(t: => Throwable): Unit =
		out.lockObject.synchronized
		{
			val traceLevel = getTrace
			if(traceLevel >= 0)
				out.print(StackTrace.trimmed(t, traceLevel))
			if(traceLevel <= 2)
				for(msg <- suppressedMessage(new SuppressedTraceContext(traceLevel, ansiCodesSupported && useColor)))
					printLabeledLine(labelColor(Level.Error), "trace", messageColor(Level.Error), msg)
		}
	def log(level: Level.Value, message: => String)
	{
		if(atLevel(level))
			log(labelColor(level), level.toString, messageColor(level), message)
	}
	private def reset(): Unit = setColor(RESET)
	
	private def setColor(color: String)
	{
		if(ansiCodesSupported && useColor)
			out.lockObject.synchronized { out.print(color) }
	}
	private def log(labelColor: String, label: String, messageColor: String, message: String): Unit =
		out.lockObject.synchronized
		{
			for(line <- message.split("""\n"""))
				printLabeledLine(labelColor, label, messageColor, line)
		}
	private def printLabeledLine(labelColor: String, label: String, messageColor: String, line: String): Unit =
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

	def logAll(events: Seq[LogEvent]) = out.lockObject.synchronized { events.foreach(log) }
	def control(event: ControlEvent.Value, message: => String)
		{ log(labelColor(Level.Info), Level.Info.toString, BLUE, message) }
}
final class SuppressedTraceContext(val traceLevel: Int, val useColor: Boolean)
sealed trait ConsoleOut
{
	val lockObject: AnyRef
	def print(s: String): Unit
	def println(s: String): Unit
	def println(): Unit
}