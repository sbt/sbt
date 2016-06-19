/* sbt -- Simple Build Tool
 * Copyright 2008, 2009  Mark Harrah
 */
package sbt.internal.util

import jline.console.ConsoleReader
import jline.console.history.{ FileHistory, MemoryHistory }
import java.io.{ File, InputStream, FileInputStream, FileDescriptor, FilterInputStream }
import complete.Parser
import scala.concurrent.duration.Duration
import scala.annotation.tailrec

abstract class JLine extends LineReader {
  protected[this] val handleCONT: Boolean
  protected[this] val reader: ConsoleReader

  def readLine(prompt: String, mask: Option[Char] = None) = JLine.withJLine { unsynchronizedReadLine(prompt, mask) }

  private[this] def unsynchronizedReadLine(prompt: String, mask: Option[Char]): Option[String] =
    readLineWithHistory(prompt, mask) map { x =>
      x.trim
    }

  private[this] def readLineWithHistory(prompt: String, mask: Option[Char]): Option[String] =
    reader.getHistory match {
      case fh: FileHistory =>
        try { readLineDirect(prompt, mask) }
        finally { fh.flush() }
      case _ => readLineDirect(prompt, mask)
    }

  private[this] def readLineDirect(prompt: String, mask: Option[Char]): Option[String] =
    if (handleCONT)
      Signals.withHandler(() => resume(), signal = Signals.CONT)(() => readLineDirectRaw(prompt, mask))
    else
      readLineDirectRaw(prompt, mask)
  private[this] def readLineDirectRaw(prompt: String, mask: Option[Char]): Option[String] =
    {
      val newprompt = handleMultilinePrompt(prompt)
      mask match {
        case Some(m) => Option(reader.readLine(newprompt, m))
        case None    => Option(reader.readLine(newprompt))
      }
    }

  private[this] def handleMultilinePrompt(prompt: String): String = {
    val lines = """\r?\n""".r.split(prompt)
    lines.length match {
      case 0 | 1 => prompt
      case _ =>
        // Workaround for regression jline/jline2#205
        reader.getOutput.write(lines.init.mkString("\n") + "\n")
        lines.last
    }
  }

  private[this] def resume(): Unit = {
    jline.TerminalFactory.reset
    JLine.terminal.init
    reader.drawLine()
    reader.flush()
  }
}
private[sbt] object JLine {
  private[this] val TerminalProperty = "jline.terminal"

  fixTerminalProperty()

  // translate explicit class names to type in order to support
  //  older Scala, since it shaded classes but not the system property
  private[sbt] def fixTerminalProperty(): Unit = {
    val newValue = System.getProperty(TerminalProperty) match {
      case "jline.UnixTerminal" => "unix"
      case null if System.getProperty("sbt.cygwin") != null => "unix"
      case "jline.WindowsTerminal" => "windows"
      case "jline.AnsiWindowsTerminal" => "windows"
      case "jline.UnsupportedTerminal" => "none"
      case x => x
    }
    if (newValue != null) System.setProperty(TerminalProperty, newValue)
    ()
  }

  // When calling this, ensure that enableEcho has been or will be called.
  // TerminalFactory.get will initialize the terminal to disable echo.
  private def terminal = jline.TerminalFactory.get
  private def withTerminal[T](f: jline.Terminal => T): T =
    synchronized {
      val t = terminal
      t.synchronized { f(t) }
    }
  /**
   * For accessing the JLine Terminal object.
   * This ensures synchronized access as well as re-enabling echo after getting the Terminal.
   */
  def usingTerminal[T](f: jline.Terminal => T): T =
    withTerminal { t =>
      t.restore
      f(t)
    }
  def createReader(): ConsoleReader = createReader(None, true)
  def createReader(historyPath: Option[File], injectThreadSleep: Boolean): ConsoleReader =
    usingTerminal { t =>
      val cr = if (injectThreadSleep) {
        val originalIn = new FileInputStream(FileDescriptor.in)
        val in = new InputStreamWrapper(originalIn, Duration("50 ms"))
        new ConsoleReader(in, System.out)
      } else new ConsoleReader
      cr.setExpandEvents(false) // https://issues.scala-lang.org/browse/SI-7650
      cr.setBellEnabled(false)
      val h = historyPath match {
        case None       => new MemoryHistory
        case Some(file) => new FileHistory(file)
      }
      h.setMaxSize(MaxHistorySize)
      cr.setHistory(h)
      cr
    }
  def withJLine[T](action: => T): T =
    withTerminal { t =>
      t.init
      try { action }
      finally { t.restore }
    }

  def simple(
    historyPath: Option[File],
    handleCONT: Boolean = HandleCONT,
    injectThreadSleep: Boolean = false
  ): SimpleReader = new SimpleReader(historyPath, handleCONT, injectThreadSleep)
  val MaxHistorySize = 500
  val HandleCONT = !java.lang.Boolean.getBoolean("sbt.disable.cont") && Signals.supported(Signals.CONT)
}

private[sbt] class InputStreamWrapper(is: InputStream, val poll: Duration) extends FilterInputStream(is) {
  @tailrec
  final override def read(): Int =
    if (is.available() != 0) is.read()
    else {
      Thread.sleep(poll.toMillis)
      read()
    }
}

trait LineReader {
  def readLine(prompt: String, mask: Option[Char] = None): Option[String]
}
final class FullReader(
  historyPath: Option[File],
  complete: Parser[_],
  val handleCONT: Boolean = JLine.HandleCONT,
  val injectThreadSleep: Boolean = false
) extends JLine {
  protected[this] val reader =
    {
      val cr = JLine.createReader(historyPath, injectThreadSleep)
      sbt.internal.util.complete.JLineCompletion.installCustomCompletor(cr, complete)
      cr
    }
}

class SimpleReader private[sbt] (historyPath: Option[File], val handleCONT: Boolean, val injectThreadSleep: Boolean) extends JLine {
  protected[this] val reader = JLine.createReader(historyPath, injectThreadSleep)
}
object SimpleReader extends SimpleReader(None, JLine.HandleCONT, false)

