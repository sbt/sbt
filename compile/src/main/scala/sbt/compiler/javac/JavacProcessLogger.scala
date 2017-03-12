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
