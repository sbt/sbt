package sbt.internal.util

import sbt.util._
import java.io.{ PrintStream, PrintWriter }
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import org.apache.logging.log4j.{ Level => XLevel }
import org.apache.logging.log4j.message.{ Message, ParameterizedMessage, ObjectMessage, ReusableObjectMessage }
import org.apache.logging.log4j.core.{ LogEvent => XLogEvent }
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.layout.PatternLayout
import org.apache.logging.log4j.core.async.RingBufferLogEvent

import ConsoleAppender._

object ConsoleLogger {
  // These are provided so other modules do not break immediately.
  @deprecated("Use ConsoleAppender.", "0.13.x")
  final val ESC = ConsoleAppender.ESC
  @deprecated("Use ConsoleAppender.", "0.13.x")
  private[sbt] def isEscapeTerminator(c: Char): Boolean = ConsoleAppender.isEscapeTerminator(c)
  @deprecated("Use ConsoleAppender.", "0.13.x")
  def hasEscapeSequence(s: String): Boolean = ConsoleAppender.hasEscapeSequence(s)
  @deprecated("Use ConsoleAppender.", "0.13.x")
  def removeEscapeSequences(s: String): String = ConsoleAppender.removeEscapeSequences(s)
  @deprecated("Use ConsoleAppender.", "0.13.x")
  val formatEnabled = ConsoleAppender.formatEnabled
  @deprecated("Use ConsoleAppender.", "0.13.x")
  val noSuppressedMessage = ConsoleAppender.noSuppressedMessage

  def apply(out: PrintStream): ConsoleLogger = apply(ConsoleOut.printStreamOut(out))
  def apply(out: PrintWriter): ConsoleLogger = apply(ConsoleOut.printWriterOut(out))
  def apply(out: ConsoleOut = ConsoleOut.systemOut, ansiCodesSupported: Boolean = ConsoleAppender.formatEnabled,
    useColor: Boolean = ConsoleAppender.formatEnabled, suppressedMessage: SuppressedTraceContext => Option[String] = ConsoleAppender.noSuppressedMessage): ConsoleLogger =
    new ConsoleLogger(out, ansiCodesSupported, useColor, suppressedMessage)
}

/**
 * A logger that logs to the console.  On supported systems, the level labels are
 * colored.
 */
class ConsoleLogger private[ConsoleLogger] (val out: ConsoleOut, override val ansiCodesSupported: Boolean, val useColor: Boolean, val suppressedMessage: SuppressedTraceContext => Option[String]) extends BasicLogger {
  private[sbt] val appender = ConsoleAppender(generateName, out, ansiCodesSupported, useColor, suppressedMessage)

  override def control(event: ControlEvent.Value, message: => String): Unit =
    appender.control(event, message)
  override def log(level: Level.Value, message: => String): Unit =
    {
      if (atLevel(level)) {
        appender.appendLog(level, message)
      }
    }

  override def success(message: => String): Unit =
    {
      if (successEnabled) {
        appender.success(message)
      }
    }
  override def trace(t: => Throwable): Unit =
    appender.trace(t, getTrace)

  override def logAll(events: Seq[LogEvent]) = out.lockObject.synchronized { events.foreach(log) }
}

object ConsoleAppender {
  /** Escape character, used to introduce an escape sequence. */
  final val ESC = '\u001B'

  /**
   * An escape terminator is a character in the range `@` (decimal value 64) to `~` (decimal value 126).
   * It is the final character in an escape sequence.
   *
   * cf. http://en.wikipedia.org/wiki/ANSI_escape_code#CSI_codes
   */
  private[sbt] def isEscapeTerminator(c: Char): Boolean =
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
    if (escIndex < 0) {
      sb.append(s, start, s.length)
      ()
    } else {
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

  val formatEnabled: Boolean =
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

  def apply(out: PrintStream): ConsoleAppender = apply(generateName, ConsoleOut.printStreamOut(out))
  def apply(out: PrintWriter): ConsoleAppender = apply(generateName, ConsoleOut.printWriterOut(out))
  def apply(): ConsoleAppender = apply(generateName, ConsoleOut.systemOut)
  def apply(name: String): ConsoleAppender = apply(name, ConsoleOut.systemOut, formatEnabled, formatEnabled, noSuppressedMessage)
  def apply(out: ConsoleOut): ConsoleAppender = apply(generateName, out, formatEnabled, formatEnabled, noSuppressedMessage)
  def apply(name: String, out: ConsoleOut): ConsoleAppender = apply(name, out, formatEnabled, formatEnabled, noSuppressedMessage)
  def apply(name: String, out: ConsoleOut, suppressedMessage: SuppressedTraceContext => Option[String]): ConsoleAppender =
    apply(name, out, formatEnabled, formatEnabled, suppressedMessage)
  def apply(name: String, out: ConsoleOut, useColor: Boolean): ConsoleAppender =
    apply(name, out, formatEnabled, useColor, noSuppressedMessage)
  def apply(name: String, out: ConsoleOut, ansiCodesSupported: Boolean,
    useColor: Boolean, suppressedMessage: SuppressedTraceContext => Option[String]): ConsoleAppender =
    {
      val appender = new ConsoleAppender(name, out, ansiCodesSupported, useColor, suppressedMessage)
      appender.start
      appender
    }
  def generateName: String =
    "out-" + generateId.incrementAndGet
  private val generateId: AtomicInteger = new AtomicInteger

  private[this] val EscapeSequence = (27.toChar + "[^@-~]*[@-~]").r
  def stripEscapeSequences(s: String): String =
    EscapeSequence.pattern.matcher(s).replaceAll("")

  def toLevel(level: XLevel): Level.Value =
    level match {
      case XLevel.OFF   => Level.Debug
      case XLevel.FATAL => Level.Error
      case XLevel.ERROR => Level.Error
      case XLevel.WARN  => Level.Warn
      case XLevel.INFO  => Level.Info
      case XLevel.DEBUG => Level.Debug
      case _            => Level.Debug
    }
  def toXLevel(level: Level.Value): XLevel =
    level match {
      case Level.Error => XLevel.ERROR
      case Level.Warn  => XLevel.WARN
      case Level.Info  => XLevel.INFO
      case Level.Debug => XLevel.DEBUG
    }
}

// See http://stackoverflow.com/questions/24205093/how-to-create-a-custom-appender-in-log4j2
// for custom appender using Java.
// http://logging.apache.org/log4j/2.x/manual/customconfig.html
// https://logging.apache.org/log4j/2.x/log4j-core/apidocs/index.html

/**
 * A logger that logs to the console.  On supported systems, the level labels are
 * colored.
 *
 * This logger is not thread-safe.
 */
class ConsoleAppender private[ConsoleAppender] (
  val name: String,
  val out: ConsoleOut,
  val ansiCodesSupported: Boolean,
  val useColor: Boolean,
  val suppressedMessage: SuppressedTraceContext => Option[String]
) extends AbstractAppender(name, null, PatternLayout.createDefaultLayout(), true) {
  import scala.Console.{ BLUE, GREEN, RED, RESET, YELLOW }

  def append(event: XLogEvent): Unit =
    {
      val level = ConsoleAppender.toLevel(event.getLevel)
      val message = event.getMessage
      val str = messageToString(message)
      appendLog(level, str)
    }

  def messageToString(msg: Message): String =
    msg match {
      case p: ParameterizedMessage  => p.getFormattedMessage
      case r: RingBufferLogEvent    => r.getFormattedMessage
      case o: ObjectMessage         => objectToString(o.getParameter)
      case o: ReusableObjectMessage => objectToString(o.getParameter)
      case _                        => msg.getFormattedMessage
    }
  def objectToString(o: AnyRef): String =
    o match {
      case x: ChannelLogEntry   => x.message
      case x: ObjectLogEntry[_] => x.message.toString
      case _                    => o.toString
    }

  def messageColor(level: Level.Value) = RESET
  def labelColor(level: Level.Value) =
    level match {
      case Level.Error => RED
      case Level.Warn  => YELLOW
      case _           => RESET
    }

  // success is called by ConsoleLogger.
  // This should turn into an event.
  private[sbt] def success(message: => String): Unit = {
    appendLog(successLabelColor, Level.SuccessLabel, successMessageColor, message)
  }
  private[sbt] def successLabelColor = GREEN
  private[sbt] def successMessageColor = RESET

  def trace(t: => Throwable, traceLevel: Int): Unit =
    out.lockObject.synchronized {
      if (traceLevel >= 0)
        out.print(StackTrace.trimmed(t, traceLevel))
      if (traceLevel <= 2)
        for (msg <- suppressedMessage(new SuppressedTraceContext(traceLevel, ansiCodesSupported && useColor)))
          printLabeledLine(labelColor(Level.Error), "trace", messageColor(Level.Error), msg)
    }

  def control(event: ControlEvent.Value, message: => String): Unit =
    appendLog(labelColor(Level.Info), Level.Info.toString, BLUE, message)

  def appendLog(level: Level.Value, message: => String): Unit = {
    appendLog(labelColor(level), level.toString, messageColor(level), message)
  }
  private def reset(): Unit = setColor(RESET)

  private def setColor(color: String): Unit = {
    if (ansiCodesSupported && useColor)
      out.lockObject.synchronized { out.print(color) }
  }
  private def appendLog(labelColor: String, label: String, messageColor: String, message: String): Unit =
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
      out.print(s" ($name)")
      out.println()
    }
}

final class SuppressedTraceContext(val traceLevel: Int, val useColor: Boolean)
