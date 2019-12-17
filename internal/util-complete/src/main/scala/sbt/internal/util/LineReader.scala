/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import java.io._

import jline.console.ConsoleReader
import jline.console.history.{ FileHistory, MemoryHistory }
import sbt.internal.util.complete.Parser

import scala.annotation.tailrec
import scala.concurrent.duration._

abstract class JLine extends LineReader {
  protected[this] def handleCONT: Boolean
  protected[this] def reader: ConsoleReader
  @deprecated("For binary compatibility only", "1.4.0")
  protected[this] def injectThreadSleep: Boolean = false
  @deprecated("For binary compatibility only", "1.4.0")
  protected[this] lazy val in: InputStream = Terminal.wrappedSystemIn

  override def readLine(prompt: String, mask: Option[Char] = None): Option[String] =
    try {
      Terminal.withRawSystemIn(unsynchronizedReadLine(prompt, mask))
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
      Signals.withHandler(() => resume(), signal = Signals.CONT)(
        () => readLineDirectRaw(prompt, mask)
      )
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
    val lines0 = """\r?\n""".r.split(prompt)
    lines0.length match {
      case 0 | 1 => handleProgress(prompt)
      case _ =>
        val lines = lines0.toList map handleProgress
        // Workaround for regression jline/jline2#205
        reader.getOutput.write(lines.init.mkString("\n") + "\n")
        lines.last
    }
  }

  private[this] def handleProgress(prompt: String): String = {
    import ConsoleAppender._
    if (showProgress) s"$DeleteLine" + prompt
    else prompt
  }

  private[this] def resume(): Unit = {
    Terminal.reset()
    reader.drawLine()
    reader.flush()
  }
}

private[sbt] object JLine {
  @deprecated("For binary compatibility only", "1.4.0")
  protected[this] val originalIn = new FileInputStream(FileDescriptor.in)

  @deprecated("Handled by Terminal.fixTerminalProperty", "1.4.0")
  private[sbt] def fixTerminalProperty(): Unit = ()

  @deprecated("For binary compatibility only", "1.4.0")
  private[sbt] def makeInputStream(injectThreadSleep: Boolean): InputStream =
    if (injectThreadSleep) new InputStreamWrapper(originalIn, 2.milliseconds)
    else originalIn

  // When calling this, ensure that enableEcho has been or will be called.
  // TerminalFactory.get will initialize the terminal to disable echo.
  @deprecated("Don't use jline.Terminal directly", "1.4.0")
  private[sbt] def terminal: jline.Terminal = Terminal.deprecatedTeminal

  /**
   * For accessing the JLine Terminal object.
   * This ensures synchronized access as well as re-enabling echo after getting the Terminal.
   */
  @deprecated("Don't use jline.Terminal directly. Use Terminal.withCanonicalIn instead.", "1.4.0")
  def usingTerminal[T](f: jline.Terminal => T): T =
    Terminal.withCanonicalIn(f(Terminal.deprecatedTeminal))

  def createReader(): ConsoleReader = createReader(None, Terminal.wrappedSystemIn)

  def createReader(historyPath: Option[File], in: InputStream): ConsoleReader = {
    val cr = Terminal.createReader(in)
    cr.setExpandEvents(false) // https://issues.scala-lang.org/browse/SI-7650
    cr.setBellEnabled(false)
    val h = historyPath match {
      case None       => new MemoryHistory
      case Some(file) => new FileHistory(file): MemoryHistory
    }
    h.setMaxSize(MaxHistorySize)
    cr.setHistory(h)
    cr
  }

  @deprecated("Avoid referencing JLine directly. Use Terminal.withRawSystemIn instead.", "1.4.0")
  def withJLine[T](action: => T): T = Terminal.withRawSystemIn(action)

  def simple(
      historyPath: Option[File],
      handleCONT: Boolean = HandleCONT,
      injectThreadSleep: Boolean = false
  ): SimpleReader = new SimpleReader(historyPath, handleCONT, injectThreadSleep)

  val MaxHistorySize = 500

  val HandleCONT =
    !java.lang.Boolean.getBoolean("sbt.disable.cont") && Signals.supported(Signals.CONT)
}

@deprecated("For binary compatibility only", "1.4.0")
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
    val handleCONT: Boolean,
    inputStream: InputStream,
) extends JLine {
  @deprecated("Use the constructor with no injectThreadSleep parameter", "1.4.0")
  def this(
      historyPath: Option[File],
      complete: Parser[_],
      handleCONT: Boolean = JLine.HandleCONT,
      injectThreadSleep: Boolean = false
  ) = this(historyPath, complete, handleCONT, JLine.makeInputStream(injectThreadSleep))
  protected[this] val reader: ConsoleReader = {
    val cr = JLine.createReader(historyPath, inputStream)
    sbt.internal.util.complete.JLineCompletion.installCustomCompletor(cr, complete)
    cr
  }
}

class SimpleReader private[sbt] (
    historyPath: Option[File],
    val handleCONT: Boolean,
    inputStream: InputStream
) extends JLine {
  def this(historyPath: Option[File], handleCONT: Boolean, injectThreadSleep: Boolean) =
    this(historyPath, handleCONT, Terminal.wrappedSystemIn)
  protected[this] val reader: ConsoleReader =
    JLine.createReader(historyPath, inputStream)
}

object SimpleReader extends SimpleReader(None, JLine.HandleCONT, false)
