/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import sbt.util._
import org.scalacheck._
import Arbitrary._
import Gen.{ listOfN, oneOf }
import Prop._

import java.io.Writer

object LogWriterTest extends Properties("Log Writer") {
  final val MaxLines = 100
  final val MaxSegments = 10

  /* Tests that content written through a LoggerWriter is properly passed to the underlying Logger.
   * Each line, determined by the specified newline separator, must be logged at the correct logging level. */
  property("properly logged") = forAll { (output: Output, newLine: NewLine) =>
    import output.{ lines, level }
    val log = new RecordingLogger
    val writer = new LoggerWriter(log, Some(level), newLine.str)
    logLines(writer, lines, newLine.str)
    val events = log.getEvents
    ("Recorded:\n" + events.map(show).mkString("\n")) |:
      check(toLines(lines), events, level)
  }

  /**
   * Displays a LogEvent in a useful format for debugging. In particular, we are only interested in
   * `Log` types and non-printable characters should be escaped
   */
  def show(event: LogEvent): String =
    event match {
      case l: Log => "Log('" + Escape(l.msg) + "', " + l.level + ")"
      case _      => "Not Log"
    }

  /**
   * Writes the given lines to the Writer. `lines` is taken to be a list of lines, which are
   * represented as separately written segments (ToLog instances). ToLog.`byCharacter` indicates
   * whether to write the segment by character (true) or all at once (false)
   */
  def logLines(writer: Writer, lines: List[List[ToLog]], newLine: String): Unit = {
    for (line <- lines; section <- line) {
      val content = section.content
      val normalized = Escape.newline(content, newLine)
      if (section.byCharacter)
        normalized.foreach(c => writer.write(c.toInt))
      else
        writer.write(normalized)
    }
    writer.flush()
  }

  /**
   * Converts the given lines in segments to lines as Strings for checking the results of the test.
   */
  def toLines(lines: List[List[ToLog]]): List[String] =
    lines.map(_.map(_.contentOnly).mkString)

  /** Checks that the expected `lines` were recorded as `events` at level `Lvl`. */
  def check(lines: List[String], events: List[LogEvent], Lvl: Level.Value): Boolean =
    (lines zip events) forall {
      case (line, log: Log) => log.level == Lvl && line == log.msg
      case _                => false
    }

  /* The following are implicit generators to build up a write sequence.
   * ToLog represents a written segment.  NewLine represents one of the possible
   * newline separators.  A List[ToLog] represents a full line and always includes a
   * final ToLog with a trailing '\n'.  Newline characters are otherwise not present in
   * the `content` of a ToLog instance.*/

  implicit lazy val arbOut: Arbitrary[Output] = Arbitrary(genOutput)
  implicit lazy val arbLog: Arbitrary[ToLog] = Arbitrary(genLog)
  implicit lazy val arbLine: Arbitrary[List[ToLog]] = Arbitrary(genLine)
  implicit lazy val arbNewLine: Arbitrary[NewLine] = Arbitrary(genNewLine)
  implicit lazy val arbLevel: Arbitrary[Level.Value] = Arbitrary(genLevel)

  implicit def genLine(using logG: Gen[ToLog]): Gen[List[ToLog]] =
    for (l <- listOf[ToLog](MaxSegments); last <- logG)
      yield (addNewline(last) :: l.filter(!_.content.isEmpty)).reverse

  implicit def genLog(using content: Arbitrary[String], byChar: Arbitrary[Boolean]): Gen[ToLog] =
    for (c <- content.arbitrary; by <- byChar.arbitrary) yield {
      assert(c != null)
      new ToLog(removeNewlines(c), by)
    }

  given genNewLine: Gen[NewLine] =
    for (str <- oneOf("\n", "\r", "\r\n")) yield new NewLine(str)

  given genLevel: Gen[Level.Value] =
    oneOf(Level.values.toSeq)

  given genOutput: Gen[Output] =
    for (ls <- listOf[List[ToLog]](MaxLines); lv <- genLevel) yield new Output(ls, lv)

  def removeNewlines(s: String) = s.replaceAll("""[\n\r]+""", "")
  def addNewline(l: ToLog): ToLog =
    new ToLog(
      l.content + "\n",
      l.byCharacter
    ) // \n will be replaced by a random line terminator for all lines

  def listOf[T](max: Int)(using content: Arbitrary[T]): Gen[List[T]] =
    Gen.choose(0, max) flatMap (sz => listOfN(sz, content.arbitrary))
}

/* Helper classes*/

final class Output(val lines: List[List[ToLog]], val level: Level.Value) {
  override def toString =
    "Level: " + level + "\n" + lines.map(_.mkString).mkString("\n")
}

final class NewLine(val str: String) {
  override def toString = Escape(str)
}

final class ToLog(val content: String, val byCharacter: Boolean) {
  def contentOnly = Escape.newline(content, "")

  override def toString =
    if (content.isEmpty) "" else "ToLog('" + Escape(contentOnly) + "', " + byCharacter + ")"
}

/** Defines some utility methods for escaping unprintable characters. */
object Escape {

  /** Escapes characters with code less than 20 by printing them as unicode escapes. */
  def apply(s: String): String = {
    val builder = new StringBuilder(s.length)
    for (c <- s) {
      val char = c.toInt
      def escaped = pad(char.toHexString.toUpperCase, 4, '0')
      if (c < 20) builder.append("\\u").append(escaped) else builder.append(c)
    }
    builder.toString
  }

  def pad(s: String, minLength: Int, extra: Char) = {
    val diff = minLength - s.length
    if (diff <= 0) s else List.fill(diff)(extra).mkString("", "", s)
  }

  /** Replaces a \n character at the end of a string `s` with `nl`. */
  def newline(s: String, nl: String): String =
    if (s.endsWith("\n")) s.substring(0, s.length - 1) + nl else s

}

/** Records logging events for later retrieval. */
final class RecordingLogger extends BasicLogger {
  private var events: List[LogEvent] = Nil

  def getEvents = events.reverse

  override def ansiCodesSupported = true
  def trace(t: => Throwable): Unit = { events ::= new Trace(t) }
  def log(level: Level.Value, message: => String): Unit = { events ::= new Log(level, message) }
  def success(message: => String): Unit = { events ::= new Success(message) }
  def logAll(es: Seq[LogEvent]): Unit = { events :::= es.toList }

  def control(event: ControlEvent.Value, message: => String): Unit =
    events ::= new ControlEvent(event, message)
}
