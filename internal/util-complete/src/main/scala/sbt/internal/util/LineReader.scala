/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.internal.util

import jline.console.ConsoleReader
import jline.console.history.{ FileHistory, MemoryHistory }
import java.io.{ File, InputStream, FileInputStream, FileDescriptor, FilterInputStream }
import complete.Parser
import scala.concurrent.duration.Duration
import scala.annotation.tailrec

abstract class JLine extends LineReader {
  protected[this] def handleCONT: Boolean
  protected[this] def reader: ConsoleReader
  protected[this] def injectThreadSleep: Boolean
  protected[this] lazy val in: InputStream = {
    // On Windows InputStream#available doesn't seem to return positive number.
    JLine.makeInputStream(injectThreadSleep && !Util.isNonCygwinWindows)
  }

  def readLine(prompt: String, mask: Option[Char] = None) =
    try {
      JLine.withJLine {
        unsynchronizedReadLine(prompt, mask)
      }
    } catch {
      case _: InterruptedException =>
        // println("readLine: InterruptedException")
        Option("")
    }

  private[this] def unsynchronizedReadLine(prompt: String, mask: Option[Char]): Option[String] =
    readLineWithHistory(prompt, mask) map { x =>
      x.trim
    }

  private[this] def readLineWithHistory(prompt: String, mask: Option[Char]): Option[String] =
    reader.getHistory match {
      case fh: FileHistory =>
        try readLineDirect(prompt, mask)
        finally fh.flush()
      case _ => readLineDirect(prompt, mask)
    }

  private[this] def readLineDirect(prompt: String, mask: Option[Char]): Option[String] =
    if (handleCONT)
      Signals.withHandler(() => resume(), signal = Signals.CONT)(() =>
        readLineDirectRaw(prompt, mask))
    else
      readLineDirectRaw(prompt, mask)

  private[this] def readLineDirectRaw(prompt: String, mask: Option[Char]): Option[String] = {
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
      case _     =>
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
      case "jline.UnixTerminal"                             => "unix"
      case null if System.getProperty("sbt.cygwin") != null => "unix"
      case "jline.WindowsTerminal"                          => "windows"
      case "jline.AnsiWindowsTerminal"                      => "windows"
      case "jline.UnsupportedTerminal"                      => "none"
      case x                                                => x
    }
    if (newValue != null) System.setProperty(TerminalProperty, newValue)
    ()
  }

  protected[this] val originalIn = new FileInputStream(FileDescriptor.in)

  private[sbt] def makeInputStream(injectThreadSleep: Boolean): InputStream =
    if (injectThreadSleep) new InputStreamWrapper(originalIn, Duration("50 ms"))
    else originalIn

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
      val result = f(t)
      t.restore
      result
    }

  def createReader(): ConsoleReader = createReader(None, JLine.makeInputStream(true))

  def createReader(historyPath: Option[File], in: InputStream): ConsoleReader =
    usingTerminal { t =>
      val cr = new ConsoleReader(in, System.out)
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
      try { action } finally { t.restore }
    }

  def simple(
      historyPath: Option[File],
      handleCONT: Boolean = HandleCONT,
      injectThreadSleep: Boolean = false
  ): SimpleReader = new SimpleReader(historyPath, handleCONT, injectThreadSleep)

  val MaxHistorySize = 500

  val HandleCONT =
    !java.lang.Boolean.getBoolean("sbt.disable.cont") && Signals.supported(Signals.CONT)
}

private[sbt] class InputStreamWrapper(is: InputStream, val poll: Duration)
    extends FilterInputStream(is) {
  @tailrec final override def read(): Int =
    if (is.available() != 0) is.read()
    else {
      Thread.sleep(poll.toMillis)
      read()
    }

  @tailrec final override def read(b: Array[Byte]): Int =
    if (is.available() != 0) is.read(b)
    else {
      Thread.sleep(poll.toMillis)
      read(b)
    }

  @tailrec final override def read(b: Array[Byte], off: Int, len: Int): Int =
    if (is.available() != 0) is.read(b, off, len)
    else {
      Thread.sleep(poll.toMillis)
      read(b, off, len)
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
  protected[this] val reader = {
    val cr = JLine.createReader(historyPath, in)
    sbt.internal.util.complete.JLineCompletion.installCustomCompletor(cr, complete)
    cr
  }
}

class SimpleReader private[sbt] (
    historyPath: Option[File],
    val handleCONT: Boolean,
    val injectThreadSleep: Boolean
) extends JLine {
  protected[this] val reader = JLine.createReader(historyPath, in)
}

object SimpleReader extends SimpleReader(None, JLine.HandleCONT, false)
