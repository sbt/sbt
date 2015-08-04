/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010, 2011 Mark Harrah
 */
package sbt

import java.io.{ BufferedWriter, PrintStream, PrintWriter }
import java.util.Locale

object ConsoleLogger {
  @deprecated("Moved to ConsoleOut", "0.13.0")
  def systemOut: ConsoleOut = ConsoleOut.systemOut

  @deprecated("Moved to ConsoleOut", "0.13.0")
  def overwriteContaining(s: String): (String, String) => Boolean = ConsoleOut.overwriteContaining(s)

  @deprecated("Moved to ConsoleOut", "0.13.0")
  def systemOutOverwrite(f: (String, String) => Boolean): ConsoleOut = ConsoleOut.systemOutOverwrite(f)

  @deprecated("Moved to ConsoleOut", "0.13.0")
  def printStreamOut(out: PrintStream): ConsoleOut = ConsoleOut.printStreamOut(out)

  @deprecated("Moved to ConsoleOut", "0.13.0")
  def printWriterOut(out: PrintWriter): ConsoleOut = ConsoleOut.printWriterOut(out)

  @deprecated("Moved to ConsoleOut", "0.13.0")
  def bufferedWriterOut(out: BufferedWriter): ConsoleOut = bufferedWriterOut(out)

  /** Escape character, used to introduce an escape sequence. */
  final val ESC = '\u001B'

  /**
   * An escape terminator is a character in the range `@` (decimal value 64) to `~` (decimal value 126).
   * It is the final character in an escape sequence.
   *
   * cf. http://en.wikipedia.org/wiki/ANSI_escape_code#CSI_codes
   */
  @deprecated("No longer public.", "0.13.8")
  def isEscapeTerminator(c: Char): Boolean =
    c >= '@' && c <= '~'

  /**
   * Test if the character AFTER an ESC is the ANSI CSI.
   *
   * see: http://en.wikipedia.org/wiki/ANSI_escape_code
   *
   * The CSI (control sequence instruction) codes start with ESC + '['.   This is for testing the second character.
   *
   * There is an additional CSI (one character) that we could test for, but is not frequnetly used, and we don't
   * check for it.
   *
   * cf. http://en.wikipedia.org/wiki/ANSI_escape_code#CSI_codes
   */
  private def isCSI(c: Char): Boolean = c == '['

  /**
   * Tests whether or not a character needs to immediately terminate the ANSI sequence.
   *
   * c.f. http://en.wikipedia.org/wiki/ANSI_escape_code#Sequence_elements
   */
  private def isAnsiTwoCharacterTerminator(c: Char): Boolean =
    (c >= '@') && (c <= '_')

  /**
   * Returns true if the string contains the ESC character.
   *
   * TODO - this should handle raw CSI (not used much)
   */
  def hasEscapeSequence(s: String): Boolean =
    s.indexOf(ESC) >= 0

  /**
   * Returns the string `s` with escape sequences removed.
   * An escape sequence starts with the ESC character (decimal value 27) and ends with an escape terminator.
   * @see isEscapeTerminator
   */
  def removeEscapeSequences(s: String): String =
    if (s.isEmpty || !hasEscapeSequence(s))
      s
    else {
      val sb = new java.lang.StringBuilder
      nextESC(s, 0, sb)
      sb.toString
    }
  private[this] def nextESC(s: String, start: Int, sb: java.lang.StringBuilder): Unit = {
    val escIndex = s.indexOf(ESC, start)
    if (escIndex < 0)
      sb.append(s, start, s.length)
    else {
      sb.append(s, start, escIndex)
      val next: Int =
        // If it's a CSI we skip past it and then look for a terminator.
        if (isCSI(s.charAt(escIndex + 1))) skipESC(s, escIndex + 2)
        else if (isAnsiTwoCharacterTerminator(s.charAt(escIndex + 1))) escIndex + 2
        else {
          // There could be non-ANSI character sequences we should make sure we handle here.
          skipESC(s, escIndex + 1)
        }
      nextESC(s, next, sb)
    }
  }

  /** Skips the escape sequence starting at `i-1`.  `i` should be positioned at the character after the ESC that starts the sequence. */
  private[this] def skipESC(s: String, i: Int): Int = {
    if (i >= s.length) {
      i
    } else if (isEscapeTerminator(s.charAt(i))) {
      i + 1
    } else {
      skipESC(s, i + 1)
    }
  }

  val formatEnabled =
    {
      import java.lang.Boolean.{ getBoolean, parseBoolean }
      val value = System.getProperty("sbt.log.format")
      if (value eq null) (ansiSupported && !getBoolean("sbt.log.noformat")) else parseBoolean(value)
    }
  private[this] def jline1to2CompatMsg = "Found class jline.Terminal, but interface was expected"

  private[this] def ansiSupported =
    try {
      val terminal = jline.TerminalFactory.get
      terminal.restore // #460
      terminal.isAnsiSupported
    } catch {
      case e: Exception => !isWindows

      // sbt 0.13 drops JLine 1.0 from the launcher and uses 2.x as a normal dependency
      // when 0.13 is used with a 0.12 launcher or earlier, the JLine classes from the launcher get loaded
      // this results in a linkage error as detected below.  The detection is likely jvm specific, but the priority
      // is avoiding mistakenly identifying something as a launcher incompatibility when it is not
      case e: IncompatibleClassChangeError if e.getMessage == jline1to2CompatMsg =>
        throw new IncompatibleClassChangeError("JLine incompatibility detected.  Check that the sbt launcher is version 0.13.x or later.")
    }

  val noSuppressedMessage = (_: SuppressedTraceContext) => None

  private[this] def os = System.getProperty("os.name")
  private[this] def isWindows = os.toLowerCase(Locale.ENGLISH).indexOf("windows") >= 0

  def apply(out: PrintStream): ConsoleLogger = apply(ConsoleOut.printStreamOut(out))
  def apply(out: PrintWriter): ConsoleLogger = apply(ConsoleOut.printWriterOut(out))
  def apply(out: ConsoleOut = ConsoleOut.systemOut, ansiCodesSupported: Boolean = formatEnabled,
    useColor: Boolean = formatEnabled, suppressedMessage: SuppressedTraceContext => Option[String] = noSuppressedMessage): ConsoleLogger =
    new ConsoleLogger(out, ansiCodesSupported, useColor, suppressedMessage)

  private[this] val EscapeSequence = (27.toChar + "[^@-~]*[@-~]").r
  def stripEscapeSequences(s: String): String =
    EscapeSequence.pattern.matcher(s).replaceAll("")
}

/**
 * A logger that logs to the console.  On supported systems, the level labels are
 * colored.
 *
 * This logger is not thread-safe.
 */
class ConsoleLogger private[ConsoleLogger] (val out: ConsoleOut, override val ansiCodesSupported: Boolean, val useColor: Boolean, val suppressedMessage: SuppressedTraceContext => Option[String]) extends BasicLogger {
  import scala.Console.{ BLUE, GREEN, RED, RESET, YELLOW }
  def messageColor(level: Level.Value) = RESET
  def labelColor(level: Level.Value) =
    level match {
      case Level.Error => RED
      case Level.Warn  => YELLOW
      case _           => RESET
    }
  def successLabelColor = GREEN
  def successMessageColor = RESET
  override def success(message: => String): Unit = {
    if (successEnabled)
      log(successLabelColor, Level.SuccessLabel, successMessageColor, message)
  }
  def trace(t: => Throwable): Unit =
    out.lockObject.synchronized {
      val traceLevel = getTrace
      if (traceLevel >= 0)
        out.print(StackTrace.trimmed(t, traceLevel))
      if (traceLevel <= 2)
        for (msg <- suppressedMessage(new SuppressedTraceContext(traceLevel, ansiCodesSupported && useColor)))
          printLabeledLine(labelColor(Level.Error), "trace", messageColor(Level.Error), msg)
    }
  def log(level: Level.Value, message: => String): Unit = {
    if (atLevel(level))
      log(labelColor(level), level.toString, messageColor(level), message)
  }
  private def reset(): Unit = setColor(RESET)

  private def setColor(color: String): Unit = {
    if (ansiCodesSupported && useColor)
      out.lockObject.synchronized { out.print(color) }
  }
  private def log(labelColor: String, label: String, messageColor: String, message: String): Unit =
    out.lockObject.synchronized {
      for (line <- message.split("""\n"""))
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
  def control(event: ControlEvent.Value, message: => String): Unit = log(labelColor(Level.Info), Level.Info.toString, BLUE, message)
}
final class SuppressedTraceContext(val traceLevel: Int, val useColor: Boolean)
