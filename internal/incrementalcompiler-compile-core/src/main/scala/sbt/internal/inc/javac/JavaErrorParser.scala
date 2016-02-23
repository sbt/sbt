package sbt

package internal
package inc
package javac

import java.io.File

import sbt.util.Logger.o2m
import xsbti.{ Problem, Severity, Maybe, Position }

/** A wrapper around xsbti.Position so we can pass in Java input. */
final case class JavaPosition(_sourceFilePath: String, _line: Int, _contents: String, _offset: Int) extends Position {
  def line: Maybe[Integer] = o2m(Some(_line))
  def lineContent: String = _contents
  def offset: Maybe[Integer] = o2m(Some(_offset))
  def pointer: Maybe[Integer] = o2m(None)
  def pointerSpace: Maybe[String] = o2m(None)
  def sourcePath: Maybe[String] = o2m(Option(_sourceFilePath))
  def sourceFile: Maybe[File] = o2m(Option(new File(_sourceFilePath)))
  override def toString = s"${_sourceFilePath}:${_line}:${_offset}"
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
final case class JavaProblem(position: Position, severity: Severity, message: String) extends xsbti.Problem {
  override def category: String = "javac" // TODO - what is this even supposed to be?  For now it appears unused.
  override def toString = s"$severity @ $position - $message"
}

/** A parser that is able to parse java's error output successfully. */
class JavaErrorParser(relativeDir: File = new File(new File(".").getAbsolutePath).getCanonicalFile) extends scala.util.parsing.combinator.RegexParsers {
  // Here we track special handlers to catch "Note:" and "Warning:" lines.
  private val NOTE_LINE_PREFIXES = Array("Note: ", "\u6ce8: ", "\u6ce8\u610f\uff1a ")
  private val WARNING_PREFIXES = Array("warning", "\u8b66\u544a", "\u8b66\u544a\uff1a")
  private val END_OF_LINE = System.getProperty("line.separator")

  override val skipWhitespace = false

  val JAVAC: Parser[String] = literal("javac")
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

  // Helper to extract an integer from a string
  private object ParsedInteger {
    def unapply(s: String): Option[Int] = try Some(Integer.parseInt(s)) catch { case e: NumberFormatException => None }
  }
  // Parses a line number
  val line: Parser[Int] = allUntilChar(':') ^? {
    case ParsedInteger(x) => x
  }

  // Parses the file + lineno output of javac.
  val fileAndLineNo: Parser[(String, Int)] = {
    val linuxFile = allUntilChar(':') ^^ { _.trim() }
    val windowsRootFile = linuxFile ~ SEMICOLON ~ linuxFile ^^ { case root ~ _ ~ path => s"$root:$path" }
    val linuxOption = linuxFile ~ SEMICOLON ~ line ^^ { case f ~ _ ~ l => (f, l) }
    val windowsOption = windowsRootFile ~ SEMICOLON ~ line ^^ { case f ~ _ ~ l => (f, l) }
    (linuxOption | windowsOption)
  }

  val allUntilCharat: Parser[String] = allUntilChar('^')

  // Helper method to try to handle relative vs. absolute file pathing....
  // NOTE - this is probably wrong...
  private def findFileSource(f: String): String = {
    // If a file looks like an absolute path, leave it as is.
    def isAbsolute(f: String) =
      (f startsWith "/") || (f matches """[^\\]+:\\.*""")
    // TODO - we used to use existence checks, that may be the right way to go
    if (isAbsolute(f)) f
    else (new File(relativeDir, f)).getAbsolutePath
  }

  /** Parses an error message (not this WILL parse warning messages as error messages if used incorrectly. */
  val errorMessage: Parser[Problem] = {
    val fileLineMessage = fileAndLineNo ~ SEMICOLON ~ restOfLine ^^ {
      case (file, line) ~ _ ~ msg => (file, line, msg)
    }
    fileLineMessage ~ allUntilCharat ~ restOfLine ^^ {
      case (file, line, msg) ~ contents ~ _ =>
        new JavaProblem(
          new JavaPosition(
            findFileSource(file),
            line,
            contents + '^', // TODO - Actually parse charat position out of here.
            getOffset(contents)
          ),
          Severity.Error,
          msg
        )
    }
  }

  /** Parses javac warning messages. */
  val warningMessage: Parser[Problem] = {
    val fileLineMessage = fileAndLineNo ~ SEMICOLON ~ WARNING ~ SEMICOLON ~ restOfLine ^^ {
      case (file, line) ~ _ ~ _ ~ _ ~ msg => (file, line, msg)
    }
    fileLineMessage ~ allUntilCharat ~ restOfLine ^^ {
      case (file, line, msg) ~ contents ~ _ =>
        new JavaProblem(
          new JavaPosition(
            findFileSource(file),
            line,
            contents + "^",
            getOffset(contents)
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
  val javacError: Parser[Problem] =
    JAVAC ~ SEMICOLON ~ restOfLine ^^ {
      case _ ~ _ ~ error =>
        new JavaProblem(
          JavaNoPosition,
          Severity.Error,
          s"javac:$error"
        )
    }

  val potentialProblem: Parser[Problem] = warningMessage | errorMessage | noteMessage | javacError

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

  final def parseProblems(in: String, logger: sbt.util.Logger): Seq[Problem] =
    parse(javacOutput, in) match {
      case Success(result, _) => result
      case Failure(msg, n) =>
        logger.warn(s"Unexpected javac output at:${n.pos.longString}.  Please report to sbt-dev@googlegroups.com.")
        Seq.empty
      case Error(msg, n) =>
        logger.warn(s"Unexpected javac output at:${n.pos.longString}.  Please report to sbt-dev@googlegroups.com.")
        Seq.empty
    }

  private def getOffset(contents: String): Int =
    contents.lines.toList.lastOption map (_.length) getOrElse 0

}
