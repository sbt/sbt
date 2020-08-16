/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import java.io.{ PrintStream, PrintWriter }
import java.lang.StringBuilder
import java.nio.channels.ClosedChannelException
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicInteger }

import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.{ Appender => XAppender, LogEvent => XLogEvent }
import org.apache.logging.log4j.message.{ Message, ObjectMessage, ReusableObjectMessage }
import org.apache.logging.log4j.{ Level => XLevel }
import sbt.internal.util.ConsoleAppender._
import sbt.util._
import org.apache.logging.log4j.core.AbstractLogEvent
import org.apache.logging.log4j.message.StringFormatterMessageFactory
import java.util.concurrent.atomic.AtomicReference

object ConsoleLogger {
  // These are provided so other modules do not break immediately.
  @deprecated("Use EscHelpers.ESC instead", "0.13.x")
  final val ESC = EscHelpers.ESC
  @deprecated("Use EscHelpers.isEscapeTerminator instead", "0.13.x")
  private[sbt] def isEscapeTerminator(c: Char): Boolean = EscHelpers.isEscapeTerminator(c)
  @deprecated("Use EscHelpers.hasEscapeSequence instead", "0.13.x")
  def hasEscapeSequence(s: String): Boolean = EscHelpers.hasEscapeSequence(s)
  @deprecated("Use EscHelpers.removeEscapeSequences instead", "0.13.x")
  def removeEscapeSequences(s: String): String = EscHelpers.removeEscapeSequences(s)
  @deprecated("Use ConsoleAppender.formatEnabledInEnv instead", "0.13.x")
  lazy val formatEnabled = ConsoleAppender.formatEnabledInEnv
  @deprecated("Use ConsoleAppender.noSuppressedMessage instead", "0.13.x")
  val noSuppressedMessage = ConsoleAppender.noSuppressedMessage

  /**
   * A new `ConsoleLogger` that logs to `out`.
   *
   * @param out Where to log the messages.
   * @return A new `ConsoleLogger` that logs to `out`.
   */
  def apply(out: PrintStream): ConsoleLogger = apply(ConsoleOut.printStreamOut(out))

  /**
   * A new `ConsoleLogger` that logs to `out`.
   *
   * @param out Where to log the messages.
   * @return A new `ConsoleLogger` that logs to `out`.
   */
  def apply(out: PrintWriter): ConsoleLogger = apply(ConsoleOut.printWriterOut(out))

  /**
   * A new `ConsoleLogger` that logs to `out`.
   *
   * @param out                Where to log the messages.
   * @param ansiCodesSupported `true` if `out` supported ansi codes, `false` otherwise.
   * @param useFormat          `true` to show formatting, `false` to remove it from messages.
   * @param suppressedMessage  How to show suppressed stack traces.
   * @return A new `ConsoleLogger` that logs to `out`.
   */
  def apply(
      out: ConsoleOut = ConsoleOut.systemOut,
      ansiCodesSupported: Boolean = ConsoleAppender.formatEnabledInEnv,
      useFormat: Boolean = ConsoleAppender.formatEnabledInEnv,
      suppressedMessage: SuppressedTraceContext => Option[String] =
        ConsoleAppender.noSuppressedMessage
  ): ConsoleLogger =
    new ConsoleLogger(out, ansiCodesSupported, useFormat, suppressedMessage)
}

/**
 * A logger that logs to the console.  On supported systems, the level labels are
 * colored.
 */
class ConsoleLogger private[ConsoleLogger] (
    out: ConsoleOut,
    override val ansiCodesSupported: Boolean,
    useFormat: Boolean,
    suppressedMessage: SuppressedTraceContext => Option[String]
) extends BasicLogger {

  private[sbt] val appender: Appender =
    ConsoleAppender(generateName(), out, ansiCodesSupported, useFormat, suppressedMessage)

  override def control(event: ControlEvent.Value, message: => String): Unit =
    appender.control(event, message)

  override def log(level: Level.Value, message: => String): Unit =
    if (atLevel(level)) {
      appender.appendLog(level, message)
    }

  override def success(message: => String): Unit =
    if (successEnabled) {
      appender.success(message)
    }

  override def trace(t: => Throwable): Unit =
    appender.trace(t, getTrace)

  override def logAll(events: Seq[LogEvent]) = events.foreach(log)
}

object ConsoleAppender {
  private[sbt] def cursorLeft(n: Int): String = s"\u001B[${n}D"
  private[sbt] def cursorUp(n: Int): String = s"\u001B[${n}A"
  private[sbt] def cursorDown(n: Int): String = s"\u001B[${n}B"
  private[sbt] def scrollUp(n: Int): String = s"\u001B[${n}S"
  private[sbt] def clearScreen(n: Int): String = s"\u001B[${n}J"
  private[sbt] def clearLine(n: Int): String = s"\u001B[${n}K"
  private[sbt] final val DeleteLine = "\u001B[2K"
  private[sbt] final val ClearScreenAfterCursor = clearScreen(0)
  private[sbt] final val CursorLeft1000 = cursorLeft(1000)
  private[sbt] final val CursorDown1 = cursorDown(1)
  private[sbt] final val ClearPromptLine = CursorLeft1000 + ClearScreenAfterCursor
  private[this] val showProgressHolder: AtomicBoolean = new AtomicBoolean(false)
  def setShowProgress(b: Boolean): Unit = showProgressHolder.set(b)
  def showProgress: Boolean = showProgressHolder.get
  private[sbt] trait Properties {
    def isAnsiSupported: Boolean
    def isColorEnabled: Boolean
    def out: ConsoleOut
  }
  private[sbt] object Properties {
    def from(terminal: Terminal): Properties = new Properties {
      override def isAnsiSupported: Boolean = terminal.isAnsiSupported
      override def isColorEnabled: Boolean = terminal.isColorEnabled
      override def out = ConsoleOut.terminalOut(terminal)
    }
    def from(o: ConsoleOut, ansi: Boolean, color: Boolean): Properties = new Properties {
      override def isAnsiSupported: Boolean = ansi
      override def isColorEnabled: Boolean = color
      override def out = o
    }
  }

  /** Hide stack trace altogether. */
  val noSuppressedMessage = (_: SuppressedTraceContext) => None

  /**
   * Indicates whether formatting has been disabled in environment variables.
   * 1. -Dsbt.log.noformat=true means no formatting.
   * 2. -Dsbt.color=always/auto/never/true/false
   * 3. -Dsbt.colour=always/auto/never/true/false
   * 4. -Dsbt.log.format=always/auto/never/true/false
   */
  lazy val formatEnabledInEnv: Boolean = {
    def useColorDefault: Boolean = {
      // This approximates that both stdin and stdio are connected,
      // so by default color will be turned off for pipes and redirects.
      val hasConsole = Option(java.lang.System.console).isDefined
      ansiSupported && hasConsole
    }
    sys.props.get("sbt.log.noformat") match {
      case Some(_) => !java.lang.Boolean.getBoolean("sbt.log.noformat")
      case _ =>
        sys.props
          .get("sbt.color")
          .orElse(sys.props.get("sbt.colour"))
          .orElse(sys.props.get("sbt.log.format"))
          .flatMap({ s =>
            parseLogOption(s) match {
              case LogOption.Always => Some(true)
              case LogOption.Never  => Some(false)
              case _                => None
            }
          })
          .getOrElse(useColorDefault)
    }
  }

  private[sbt] def parseLogOption(s: String): LogOption =
    s.toLowerCase match {
      case "always" => LogOption.Always
      case "auto"   => LogOption.Auto
      case "never"  => LogOption.Never
      case "true"   => LogOption.Always
      case "false"  => LogOption.Never
      case _        => LogOption.Auto
    }

  private[this] val generateId: AtomicInteger = new AtomicInteger

  /**
   * A new `ConsoleAppender` that writes to standard output.
   *
   * @return A new `ConsoleAppender` that writes to standard output.
   */
  def apply(): Appender = apply(ConsoleOut.systemOut)

  /**
   * A new `ConsoleAppender` that appends log message to `out`.
   *
   * @param out Where to write messages.
   * @return A new `ConsoleAppender`.
   */
  def apply(out: PrintStream): Appender = apply(ConsoleOut.printStreamOut(out))

  /**
   * A new `ConsoleAppender` that appends log messages to `out`.
   *
   * @param out Where to write messages.
   * @return A new `ConsoleAppender`.
   */
  def apply(out: PrintWriter): Appender = apply(ConsoleOut.printWriterOut(out))

  /**
   * A new `ConsoleAppender` that writes to `out`.
   *
   * @param out Where to write messages.
   * @return A new `ConsoleAppender that writes to `out`.
   */
  def apply(out: ConsoleOut): Appender = apply(generateName(), out)

  /**
   * A new `ConsoleAppender` identified by `name`, and that writes to standard output.
   *
   * @param name An identifier for the `ConsoleAppender`.
   * @return A new `ConsoleAppender` that writes to standard output.
   */
  def apply(name: String): Appender = apply(name, ConsoleOut.systemOut)

  /**
   * A new `ConsoleAppender` identified by `name`, and that writes to `out`.
   *
   * @param name An identifier for the `ConsoleAppender`.
   * @param out Where to write messages.
   * @return A new `ConsoleAppender` that writes to `out`.
   */
  def apply(name: String, out: ConsoleOut): Appender = apply(name, out, formatEnabledInEnv)

  /**
   * A new `ConsoleAppender` identified by `name`, and that writes to `out`.
   *
   * @param name              An identifier for the `ConsoleAppender`.
   * @param out               Where to write messages.
   * @param suppressedMessage How to handle stack traces.
   * @return A new `ConsoleAppender` that writes to `out`.
   */
  def apply(
      name: String,
      out: ConsoleOut,
      suppressedMessage: SuppressedTraceContext => Option[String]
  ): Appender =
    apply(name, out, formatEnabledInEnv, formatEnabledInEnv, suppressedMessage)

  /**
   * A new `ConsoleAppender` identified by `name`, and that writes to `out`.
   *
   * @param name      An identifier for the `ConsoleAppender`.
   * @param out       Where to write messages.
   * @param useFormat `true` to enable format (color, bold, etc.), `false` to remove formatting.
   * @return A new `ConsoleAppender` that writes to `out`.
   */
  def apply(name: String, out: ConsoleOut, useFormat: Boolean): Appender =
    apply(name, out, useFormat || formatEnabledInEnv, useFormat, noSuppressedMessage)

  /**
   * A new `ConsoleAppender` identified by `name`, and that writes to `out`.
   *
   * @param name      An identifier for the `ConsoleAppender`.
   * @param terminal  The terminal to which this appender corresponds
   * @return A new `ConsoleAppender` that writes to `out`.
   */
  def apply(name: String, terminal: Terminal): Appender = {
    new ConsoleAppender(name, Properties.from(terminal), noSuppressedMessage)
  }

  /**
   * A new `ConsoleAppender` identified by `name`, and that writes to `out`.
   *
   * @param name      An identifier for the `ConsoleAppender`.
   * @param terminal  The terminal to which this appender corresponds
   * @param suppressedMessage How to handle stack traces.
   * @return A new `ConsoleAppender` that writes to `out`.
   */
  def apply(
      name: String,
      terminal: Terminal,
      suppressedMessage: SuppressedTraceContext => Option[String]
  ): Appender = {
    new ConsoleAppender(name, Properties.from(terminal), suppressedMessage)
  }

  /**
   * A new `ConsoleAppender` identified by `name`, and that writes to `out`.
   *
   * @param name               An identifier for the `ConsoleAppender`.
   * @param out                Where to write messages.
   * @param ansiCodesSupported `true` if the output stream supports ansi codes, `false` otherwise.
   * @param useFormat          `true` to enable format (color, bold, etc.), `false` to remove
   *                           formatting.
   * @return A new `ConsoleAppender` that writes to `out`.
   */
  def apply(
      name: String,
      out: ConsoleOut,
      ansiCodesSupported: Boolean,
      useFormat: Boolean,
      suppressedMessage: SuppressedTraceContext => Option[String]
  ): Appender = {
    new ConsoleAppender(
      name,
      Properties.from(out, ansiCodesSupported, useFormat),
      suppressedMessage
    )
  }

  /**
   * Converts the Log4J `level` to the corresponding sbt level.
   *
   * @param level A level, as represented by Log4J.
   * @return The corresponding level in sbt's world.
   */
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

  /**
   * Converts the sbt `level` to the corresponding Log4J level.
   *
   * @param level A level, as represented by sbt.
   * @return The corresponding level in Log4J's world.
   */
  def toXLevel(level: Level.Value): XLevel =
    level match {
      case Level.Error => XLevel.ERROR
      case Level.Warn  => XLevel.WARN
      case Level.Info  => XLevel.INFO
      case Level.Debug => XLevel.DEBUG
    }

  private[sbt] def generateName(): String = "out-" + generateId.incrementAndGet

  private[this] def ansiSupported: Boolean = Terminal.console.isAnsiSupported
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
class ConsoleAppender(
    override private[sbt] val name: String,
    override private[sbt] val properties: Properties,
    override private[sbt] val suppressedMessage: SuppressedTraceContext => Option[String]
) extends Appender {
  private[this] val log4j = new AtomicReference[XAppender](null)
  override private[sbt] lazy val toLog4J = log4j.get match {
    case null =>
      log4j.synchronized {
        log4j.get match {
          case null =>
            val l = new Log4JConsoleAppender(name, properties, suppressedMessage, { event =>
              val level = ConsoleAppender.toLevel(event.getLevel)
              val message = event.getMessage
              try appendMessage(level, message)
              catch { case _: ClosedChannelException => }
            })
            log4j.set(l)
            l
          case l => l
        }
      }
  }
  override def close(): Unit = log4j.get match {
    case null =>
    case a    => a.stop()
  }
}
trait Appender extends AutoCloseable {
  private[sbt] def name: String
  private[sbt] def properties: Properties
  private[sbt] def suppressedMessage: SuppressedTraceContext => Option[String]
  import scala.Console.{ BLUE, GREEN, RED, YELLOW }

  private[util] def out: ConsoleOut = properties.out
  private[util] def ansiCodesSupported: Boolean = properties.isAnsiSupported
  private[util] def useFormat: Boolean = properties.isColorEnabled

  private def reset: String = {
    if (ansiCodesSupported && useFormat) scala.Console.RESET
    else ""
  }

  private val SUCCESS_LABEL_COLOR = GREEN
  private val SUCCESS_MESSAGE_COLOR = reset
  private val NO_COLOR = reset

  private var traceEnabledVar: Int = Int.MaxValue

  def setTrace(level: Int): Unit = synchronized { traceEnabledVar = level }

  /**
   * Returns the number of lines for stacktrace.
   */
  def getTrace: Int = synchronized { traceEnabledVar }

  private[sbt] def toLog4J: XAppender

  /**
   * Logs the stack trace of `t`, possibly shortening it.
   *
   * The `traceLevel` parameter configures how the stack trace will be shortened.
   * See `StackTrace.trimmed`.
   *
   * @param t          The `Throwable` whose stack trace to log.
   * @param traceLevel How to shorten the stack trace.
   */
  def trace(t: => Throwable, traceLevel: Int): Unit = {
    if (traceLevel >= 0)
      write(StackTrace.trimmed(t, traceLevel))
    if (traceLevel <= 2) {
      val ctx = new SuppressedTraceContext(traceLevel, ansiCodesSupported && useFormat)
      for (msg <- suppressedMessage(ctx))
        appendLog(NO_COLOR, "trace", NO_COLOR, msg)
    }
  }

  /**
   * Logs a `ControlEvent` to the log.
   *
   * @param event   The kind of `ControlEvent`.
   * @param message The message to log.
   */
  def control(event: ControlEvent.Value, message: => String): Unit =
    appendLog(labelColor(Level.Info), Level.Info.toString, BLUE, message)

  /**
   * Appends the message `message` to the to the log at level `level`.
   *
   * @param level   The importance level of the message.
   * @param message The message to log.
   */
  def appendLog(level: Level.Value, message: => String): Unit = {
    appendLog(labelColor(level), level.toString, NO_COLOR, message)
  }

  /**
   * Select the right color for the label given `level`.
   *
   * @param level The label to consider to select the color.
   * @return The color to use to color the label.
   */
  private def labelColor(level: Level.Value): String =
    level match {
      case Level.Error => RED
      case Level.Warn  => YELLOW
      case _           => NO_COLOR
    }

  /**
   * Appends a full message to the log. Each line is prefixed with `[$label]`, written in
   * `labelColor` if formatting is enabled. The lines of the messages are colored with
   * `messageColor` if formatting is enabled.
   *
   * @param labelColor   The color to use to format the label.
   * @param label        The label to prefix each line with. The label is shown between square
   *                     brackets.
   * @param messageColor The color to use to format the message.
   * @param message      The message to write.
   */
  private def appendLog(
      labelColor: String,
      label: String,
      messageColor: String,
      message: String
  ): Unit =
    try {
      // according to https://github.com/sbt/sbt/issues/5608, sometimes we get a null message
      if (message == null) ()
      else {
        val len =
          labelColor.length + label.length + messageColor.length + reset.length * 3 + ClearScreenAfterCursor.length
        val builder: StringBuilder = new StringBuilder(len)
        message.linesIterator.foreach { line =>
          builder.ensureCapacity(len + line.length + 4)
          builder.setLength(0)

          def fmted(a: String, b: String) = builder.append(reset).append(a).append(b).append(reset)

          builder.append(reset).append('[')
          fmted(labelColor, label)
          builder.append("] ")
          fmted(messageColor, line)
          builder.append(ClearScreenAfterCursor)
          write(builder.toString)
        }
      }
    } catch { case _: InterruptedException => }

  // success is called by ConsoleLogger.
  private[sbt] def success(message: => String): Unit = {
    appendLog(SUCCESS_LABEL_COLOR, Level.SuccessLabel, SUCCESS_MESSAGE_COLOR, message)
  }

  private def write(msg: String): Unit = {
    val toWrite =
      if (!useFormat || !ansiCodesSupported) EscHelpers.removeEscapeSequences(msg) else msg
    out.println(toWrite)
  }

  private[util] def appendMessage(level: Level.Value, msg: Message): Unit =
    msg match {
      case o: ObjectMessage         => appendMessageContent(level, o.getParameter)
      case o: ReusableObjectMessage => appendMessageContent(level, o.getParameter)
      case _                        => appendLog(level, msg.getFormattedMessage)
    }

  private def appendTraceEvent(te: TraceEvent): Unit = {
    val traceLevel = getTrace
    if (traceLevel >= 0) {
      val throwableShowLines: ShowLines[Throwable] =
        ShowLines[Throwable]((t: Throwable) => {
          List(StackTrace.trimmed(t, traceLevel))
        })
      val codec: ShowLines[TraceEvent] =
        ShowLines[TraceEvent]((t: TraceEvent) => {
          throwableShowLines.showLines(t.message)
        })
      codec.showLines(te).toVector foreach { appendLog(Level.Error, _) }
    }
    if (traceLevel <= 2) {
      suppressedMessage(new SuppressedTraceContext(traceLevel, ansiCodesSupported && useFormat)) foreach {
        appendLog(Level.Error, _)
      }
    }
  }

  private def appendMessageContent(level: Level.Value, o: AnyRef): Unit = {
    def appendEvent(oe: ObjectEvent[_]): Unit = {
      val contentType = oe.contentType
      contentType match {
        case "sbt.internal.util.TraceEvent"    => appendTraceEvent(oe.message.asInstanceOf[TraceEvent])
        case "sbt.internal.util.ProgressEvent" =>
        case _ =>
          LogExchange.stringCodec[AnyRef](contentType) match {
            case Some(codec) if contentType == "sbt.internal.util.SuccessEvent" =>
              codec.showLines(oe.message.asInstanceOf[AnyRef]).toVector foreach { success(_) }
            case Some(codec) =>
              codec.showLines(oe.message.asInstanceOf[AnyRef]).toVector foreach (appendLog(
                level,
                _
              ))
            case _ => appendLog(level, oe.message.toString)
          }
      }
    }

    o match {
      case x: StringEvent    => Vector(x.message) foreach { appendLog(level, _) }
      case x: ObjectEvent[_] => appendEvent(x)
      case _                 => Vector(o.toString) foreach { appendLog(level, _) }
    }
  }
  private[sbt] def appendObjectEvent[T](level: Level.Value, message: => ObjectEvent[T]): Unit =
    appendMessageContent(level, message)

}
private[internal] class Log4JConsoleAppender(
    override private[sbt] val name: String,
    override private[sbt] val properties: Properties,
    override private[sbt] val suppressedMessage: SuppressedTraceContext => Option[String],
    appendEvent: XLogEvent => Unit,
) extends AbstractAppender(name, null, LogExchange.dummyLayout, true, Array.empty)
    with Appender {
  start()
  override def close(): Unit = stop()
  override private[sbt] def toLog4J: XAppender = this
  override def append(event: XLogEvent): Unit = appendEvent(event)
}
private[sbt] class ConsoleAppenderFromLog4J(
    override private[sbt] val name: String,
    override private[sbt] val properties: Properties,
    override private[sbt] val suppressedMessage: SuppressedTraceContext => Option[String],
    val delegate: XAppender,
) extends Appender {
  def this(name: String, delegate: XAppender) =
    this(name, Properties.from(Terminal.get), _ => None, delegate)
  override def close(): Unit = delegate.stop()
  private[sbt] def toLog4J: XAppender = delegate
  override def appendLog(level: sbt.util.Level.Value, message: => String): Unit = {
    delegate.append(new AbstractLogEvent {
      override def getLevel(): XLevel = ConsoleAppender.toXLevel(level)
      override def getMessage(): Message =
        StringFormatterMessageFactory.INSTANCE.newMessage(message.toString, Array.empty)
    })
  }
}

final class SuppressedTraceContext(val traceLevel: Int, val useFormat: Boolean)
