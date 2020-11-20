/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import java.io._
import java.util.{ List => JList }

import jline.console.ConsoleReader
import jline.console.history.{ FileHistory, MemoryHistory }
import org.jline.reader.{
  Candidate,
  Completer,
  EndOfFileException,
  LineReader => JLineReader,
  LineReaderBuilder,
  ParsedLine,
  UserInterruptException,
}
import org.jline.utils.ClosedException
import sbt.internal.util.complete.Parser
import sbt.io.syntax._

import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.util.control.NonFatal
import java.nio.channels.ClosedByInterruptException
import java.net.MalformedURLException

import org.jline.builtins.InputRC

trait LineReader extends AutoCloseable {
  def readLine(prompt: String, mask: Option[Char] = None): Option[String]
  override def close(): Unit = {}
}

object LineReader {
  val HandleCONT =
    !java.lang.Boolean.getBoolean("sbt.disable.cont") && Signals.supported(Signals.CONT)
  val MaxHistorySize = 500

  private def completer(parser: Parser[_]): Completer = new Completer {
    def complete(lr: JLineReader, pl: ParsedLine, candidates: JList[Candidate]): Unit = {
      Parser.completions(parser, pl.line(), 10).get.foreach { c =>
        /*
         * For commands like `~` that delegate parsing to another parser, the `~` may be
         * excluded from the completion result. For example,
         * ~testOnly <TAB>
         * might return results like
         * 'testOnly ;'
         * 'testOnly com.foo.FooSpec'
         * ...
         * If we use the raw display, JLine will reject the completions because they are
         * missing the leading `~`. To workaround this, we append to the result to the
         * line provided the line does not end with " ". This fixes the missing `~` in
         * the prefix problem. We also need to split the line on space and take the
         * last token and append to that otherwise the completion will double print
         * the prefix, so that `testOnly com<Tab>` might expand to something like:
         * `testOnly testOnly\ com.foo.FooSpec` instead of `testOnly com.foo.FooSpec`.
         */
        if (c.append.nonEmpty) {
          val comp =
            if (!pl.line().endsWith(" ")) pl.line().split(" ").last + c.append else c.append
          // tell jline to append a " " if the completion would be valid with a " " appended
          // which can be the case for input tasks and some commands. We need to exclude
          // the empty string and ";" which always seem to be present.
          val complete = (Parser.completions(parser, comp + " ", 10).get.map(_.display) --
            Set(";", "")).nonEmpty
          candidates.add(new Candidate(comp, comp, null, null, null, null, complete))
        }
      }
    }
  }
  private[this] def inputrcFileUrl(): Option[URL] = {
    // keep jline2 compatibility
    // https://github.com/jline/jline2/blob/12b98d94589e3bd6a6/src/main/java/jline/console/ConsoleReader.java#L291-L306
    sys.props
      .get("jline.inputrc")
      .flatMap { path =>
        try {
          Some(url(path))
        } catch {
          case _: MalformedURLException =>
            Some(file(path).toURI.toURL)
        }
      }
      .orElse {
        sys.props.get("user.home").map { home =>
          val f = file(home) / ".inputrc"
          (if (f.isFile) f else file("/etc/inputrc")).toURI.toURL
        }
      }
  }
  // cache on memory.
  private[this] lazy val inputrcFileContents: Option[Array[Byte]] =
    inputrcFileUrl().map(in => sbt.io.IO.readBytes(in.openStream()))
  def createReader(
      historyPath: Option[File],
      parser: Parser[_],
      terminal: Terminal,
  ): LineReader = {
    // We may want to consider insourcing LineReader.java from jline. We don't otherwise
    // directly need jline3 for sbt.
    new LineReader {
      override def readLine(prompt: String, mask: Option[Char]): Option[String] = {
        val term = JLine3(terminal)
        val reader = LineReaderBuilder.builder().terminal(term).completer(completer(parser)).build()
        try {
          inputrcFileContents.foreach { bytes =>
            InputRC.configure(
              reader,
              new ByteArrayInputStream(bytes)
            )
          }
        } catch {
          case NonFatal(_) =>
          // ignore
        }
        historyPath.foreach(f => reader.setVariable(JLineReader.HISTORY_FILE, f))
        try terminal.withRawInput {
          Option(mask.map(reader.readLine(prompt, _)).getOrElse(reader.readLine(prompt)))
        } catch {
          case e: EndOfFileException =>
            if (terminal == Terminal.console && System.console == null) None
            else Some("exit")
          case _: IOError | _: ClosedException => Some("exit")
          case _: UserInterruptException | _: ClosedByInterruptException |
              _: UncheckedIOException =>
            throw new InterruptedException
        } finally {
          terminal.prompt.reset()
          term.close()
        }
      }
    }
  }

  def createJLine2Reader(
      historyPath: Option[File],
      terminal: Terminal,
      prompt: Prompt = Prompt.Running,
  ): ConsoleReader = {
    val cr = Terminal.createReader(terminal, prompt)
    cr.setExpandEvents(false) // https://issues.scala-lang.org/browse/SI-7650
    cr.setBellEnabled(false)
    val h = historyPath match {
      case None       => new MemoryHistory
      case Some(file) => new FileHistory(file): MemoryHistory
    }
    h.setMaxSize(MaxHistorySize)
    cr.setHistory(h)
    cr.setHistoryEnabled(true)
    cr
  }
  def simple(terminal: Terminal): LineReader = new SimpleReader(None, HandleCONT, terminal)
  def simple(
      historyPath: Option[File],
      handleCONT: Boolean = HandleCONT,
      injectThreadSleep: Boolean = false
  ): LineReader = new SimpleReader(historyPath, handleCONT, injectThreadSleep)
}

abstract class JLine extends LineReader {
  protected[this] def handleCONT: Boolean
  protected[this] def reader: ConsoleReader
  @deprecated("For binary compatibility only", "1.4.0")
  protected[this] def injectThreadSleep: Boolean = false
  @deprecated("For binary compatibility only", "1.4.0")
  protected[this] lazy val in: InputStream = Terminal.wrappedSystemIn

  override def readLine(prompt: String, mask: Option[Char] = None): Option[String] =
    try {
      unsynchronizedReadLine(prompt, mask)
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

@deprecated("Use LineReader apis", "1.4.0")
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
  @deprecated(
    "Don't use jline.Terminal directly. Use Terminal.get.withCanonicalIn instead.",
    "1.4.0"
  )
  def usingTerminal[T](f: jline.Terminal => T): T = f(Terminal.get.toJLine)

  @deprecated("unused", "1.4.0")
  def createReader(): ConsoleReader = createReader(None, Terminal.wrappedSystemIn)

  @deprecated("Use LineReader.createReader", "1.4.0")
  def createReader(historyPath: Option[File], in: InputStream): ConsoleReader = {
    val cr = Terminal.createReader(Terminal.console, Prompt.Running)
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

  @deprecated("Avoid referencing JLine directly.", "1.4.0")
  def withJLine[T](action: => T): T = Terminal.get.withRawInput(action)

  @deprecated("Use LineReader.simple instead", "1.4.0")
  def simple(
      historyPath: Option[File],
      handleCONT: Boolean = LineReader.HandleCONT,
      injectThreadSleep: Boolean = false
  ): SimpleReader = new SimpleReader(historyPath, handleCONT, injectThreadSleep)

  @deprecated("Use LineReader.MaxHistorySize", "1.4.0")
  val MaxHistorySize = LineReader.MaxHistorySize

  @deprecated("Use LineReader.HandleCONT", "1.4.0")
  val HandleCONT = LineReader.HandleCONT
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

final class FullReader(
    historyPath: Option[File],
    complete: Parser[_],
    val handleCONT: Boolean,
    terminal: Terminal
) extends JLine {
  @deprecated("Use the constructor with no injectThreadSleep parameter", "1.4.0")
  def this(
      historyPath: Option[File],
      complete: Parser[_],
      handleCONT: Boolean = LineReader.HandleCONT,
      injectThreadSleep: Boolean = false
  ) =
    this(
      historyPath,
      complete,
      handleCONT,
      Terminal.console
    )
  protected[this] val reader: ConsoleReader = {
    val cr = LineReader.createJLine2Reader(historyPath, terminal)
    sbt.internal.util.complete.JLineCompletion.installCustomCompletor(cr, complete)
    cr
  }
}

class SimpleReader private[sbt] (
    historyPath: Option[File],
    val handleCONT: Boolean,
    terminal: Terminal
) extends JLine {
  def this(historyPath: Option[File], handleCONT: Boolean, injectThreadSleep: Boolean) =
    this(historyPath, handleCONT, Terminal.console)
  protected[this] lazy val reader: ConsoleReader =
    LineReader.createJLine2Reader(historyPath, terminal)
}

object SimpleReader extends SimpleReader(None, LineReader.HandleCONT, false) {
  def apply(terminal: Terminal): SimpleReader =
    new SimpleReader(None, LineReader.HandleCONT, terminal)
}
