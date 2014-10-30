package sbt
package compiler
package javac

import java.util.StringTokenizer

import xsbti._
import java.io.File

/**
 * An adapted process logger which can feed semantic error events from Javac as well as just
 * dump logs.
 *
 *
 * @param log  The logger where all input will go.
 * @param reporter  A reporter for semantic Javac error messages.
 * @param cwd The current working directory of the Javac process, used when parsing Filenames.
 */
final class JavacLogger(log: sbt.Logger, reporter: Reporter, cwd: File) extends ProcessLogger {
  import scala.collection.mutable.ListBuffer
  import Level.{ Info, Warn, Error, Value => LogLevel }

  private val msgs: ListBuffer[(LogLevel, String)] = new ListBuffer()

  def info(s: => String): Unit =
    synchronized { msgs += ((Info, s)) }

  def error(s: => String): Unit =
    synchronized { msgs += ((Error, s)) }

  def buffer[T](f: => T): T = f

  private def print(desiredLevel: LogLevel)(t: (LogLevel, String)) = t match {
    case (Info, msg)  => log.info(msg)
    case (Error, msg) => log.log(desiredLevel, msg)
  }

  // Helper method to dump all semantic errors.
  private def parseAndDumpSemanticErrors(): Unit = {
    val input =
      msgs collect {
        case (Error, msg) => msg
      } mkString "\n"
    val parser = new JavaErrorParser(cwd)
    parser.parseProblems(input, log) foreach { e =>
      reporter.log(e.position, e.message, e.severity)
    }
  }

  def flush(exitCode: Int): Unit = {
    parseAndDumpSemanticErrors()
    val level = if (exitCode == 0) Warn else Error
    // Here we only display things that wouldn't otherwise be output by the error reporter.
    // TODO - NOTES may not be displayed correctly!
    msgs collect {
      case (Info, msg) => msg
    } foreach { msg =>
      log.info(msg)
    }
    msgs.clear()
  }
}

import sbt.Logger.o2m

/** A wrapper around xsbti.Position so we can pass in Java input. */
final case class JavaPosition(_sourceFile: File, _line: Int, _contents: String) extends Position {
  def line: Maybe[Integer] = o2m(Option(Integer.valueOf(_line)))
  def lineContent: String = _contents
  def offset: Maybe[Integer] = o2m(None)
  def pointer: Maybe[Integer] = o2m(None)
  def pointerSpace: Maybe[String] = o2m(None)
  def sourcePath: Maybe[String] = o2m(Option(_sourceFile.getCanonicalPath))
  def sourceFile: Maybe[File] = o2m(Option(_sourceFile))
  override def toString = s"${_sourceFile}:${_line}"
}

/** A position which has no information, because there is none. */
object JavaNoPosition extends Position {
  def line: Maybe[Integer] = o2m(None)
  def lineContent: String = ""
  def offset: Maybe[Integer] = o2m(None)
  def pointer: Maybe[Integer] = o2m(None)
  def pointerSpace: Maybe[String] = o2m(None)
  def sourcePath: Maybe[String] = o2m(None)
  def sourceFile: Maybe[File] = o2m(None)
  override def toString = "NoPosition"
}

/** A wrapper around xsbti.Problem with java-specific options. */
final case class JavaProblem(val position: Position, val severity: Severity, val message: String) extends xsbti.Problem {
  override def category: String = "javac" // TODO - what is this even supposed to be?  For now it appears unused.
  override def toString = s"$severity @ $position - $message"
}

/** A parser that is able to parse java's error output successfully. */
class JavaErrorParser(relativeDir: File) extends util.parsing.combinator.RegexParsers {
  // Here we track special handlers to catch "Note:" and "Warning:" lines.
  private val NOTE_LINE_PREFIXES = Array("Note: ", "\u6ce8: ", "\u6ce8\u610f\uff1a ")
  private val WARNING_PREFIXES = Array("warning", "\u8b66\u544a", "\u8b66\u544a\uff1a")
  private val END_OF_LINE = System.getProperty("line.separator")

  override val skipWhitespace = false

  val CHARAT: Parser[String] = literal("^")
  val SEMICOLON: Parser[String] = literal(":") | literal("\uff1a")
  val SYMBOL: Parser[String] = allUntilChar(':') // We ignore whether it actually says "symbol" for i18n
  val LOCATION: Parser[String] = allUntilChar(':') // We ignore whether it actually says "location" for i18n.
  val WARNING: Parser[String] = allUntilChar(':') ^? {
    case x if WARNING_PREFIXES.exists(x.trim.startsWith) => x
  }
  // Parses the rest of an input line.
  val restOfLine: Parser[String] =
    // TODO - Can we use END_OF_LINE here without issues?
    allUntilChars(Array('\n', '\r')) ~ "[\r]?[\n]?".r ^^ {
      case msg ~ _ => msg
    }
  val NOTE: Parser[String] = restOfLine ^? {
    case x if NOTE_LINE_PREFIXES exists x.startsWith => x
  }

  // Parses ALL characters until an expected character is met.
  def allUntilChar(c: Char): Parser[String] = allUntilChars(Array(c))
  def allUntilChars(chars: Array[Char]): Parser[String] = new Parser[String] {
    def isStopChar(c: Char): Boolean = {
      var i = 0
      while (i < chars.length) {
        if (c == chars(i)) return true
        i += 1
      }
      false
    }

    def apply(in: Input) = {
      val source = in.source
      val offset = in.offset
      val start = handleWhiteSpace(source, offset)
      var i = start
      while (i < source.length && !isStopChar(source.charAt(i))) {
        i += 1
      }
      Success(source.subSequence(start, i).toString, in.drop(i - offset))
    }
  }

  //parses a file name (no checks)
  val file: Parser[String] = allUntilChar(':') ^^ { _.trim() }
  // Checks if a string is an integer
  def isInteger(s: String): Boolean =
    try {
      Integer.parseInt(s)
      true
    } catch {
      case e: NumberFormatException => false
    }

  // Helper to extract an integer from a string
  private object ParsedInteger {
    def unapply(s: String): Option[Int] = try Some(Integer.parseInt(s)) catch { case e: NumberFormatException => None }
  }
  // Parses a line number
  val line: Parser[Int] = allUntilChar(':') ^? {
    case ParsedInteger(x) => x
  }
  val allUntilCharat: Parser[String] = allUntilChar('^')

  // Helper method to try to handle relative vs. absolute file pathing....
  // NOTE - this is probably wrong...
  private def findFileSource(f: String): File = {
    val tmp = new File(f)
    if (tmp.exists) tmp
    else new File(relativeDir, f)
  }

  /** Parses an error message (not this WILL parse warning messages as error messages if used incorrectly. */
  val errorMessage: Parser[Problem] = {
    val fileLineMessage = file ~ SEMICOLON ~ line ~ SEMICOLON ~ restOfLine ^^ {
      case file ~ _ ~ line ~ _ ~ msg => (file, line, msg)
    }
    fileLineMessage ~ allUntilCharat ~ restOfLine ^^ {
      case (file, line, msg) ~ contents ~ _ =>
        new JavaProblem(
          new JavaPosition(
            findFileSource(file),
            line,
            contents + '^' // TODO - Actually parse charat position out of here.
          ),
          Severity.Error,
          msg
        )
    }
  }

  /** Parses javac warning messages. */
  val warningMessage: Parser[Problem] = {
    val fileLineMessage = file ~ SEMICOLON ~ line ~ SEMICOLON ~ WARNING ~ SEMICOLON ~ restOfLine ^^ {
      case file ~ _ ~ line ~ _ ~ _ ~ _ ~ msg => (file, line, msg)
    }
    fileLineMessage ~ allUntilCharat ~ restOfLine ^^ {
      case (file, line, msg) ~ contents ~ _ =>
        new JavaProblem(
          new JavaPosition(
            findFileSource(file),
            line,
            contents + "^"
          ),
          Severity.Warn,
          msg
        )
    }
  }
  val noteMessage: Parser[Problem] =
    NOTE ^^ { msg =>
      new JavaProblem(
        JavaNoPosition,
        Severity.Info,
        msg
      )
    }

  val potentialProblem: Parser[Problem] = warningMessage | errorMessage | noteMessage

  val javacOutput: Parser[Seq[Problem]] = rep(potentialProblem)
  /**
   * Example:
   *
   * Test.java:4: cannot find symbol
   * symbol  : method baz()
   * location: class Foo
   * return baz();
   * ^
   *
   * Test.java:8: warning: [deprecation] RMISecurityException(java.lang.String) in java.rmi.RMISecurityException has been deprecated
   * throw new java.rmi.RMISecurityException("O NOES");
   * ^
   */

  final def parseProblems(in: String, logger: sbt.Logger): Seq[Problem] =
    parse(javacOutput, in) match {
      case Success(result, _) => result
      case Failure(msg, n) =>
        logger.warn("Unexpected javac output at:${n.pos.longString}.  Please report to sbt-dev@googlegroups.com.")
        Seq.empty
      case Error(msg, n) =>
        logger.warn("Unexpected javac output at:${n.pos.longString}.  Please report to sbt-dev@googlegroups.com.")
        Seq.empty
    }

}