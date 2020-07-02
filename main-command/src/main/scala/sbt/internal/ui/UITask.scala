/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.ui

import java.io.File
import java.nio.channels.ClosedChannelException
import java.util.concurrent.atomic.AtomicBoolean

import jline.console.history.PersistentHistory
import sbt.BasicCommandStrings.{ Cancel, TerminateAction, Shutdown }
import sbt.BasicKeys.{ historyPath, terminalShellPrompt }
import sbt.State
import sbt.internal.CommandChannel
import sbt.internal.util.ConsoleAppender.{ ClearPromptLine, ClearScreenAfterCursor, DeleteLine }
import sbt.internal.util._
import sbt.internal.util.complete.{ JLineCompletion, Parser }

import scala.annotation.tailrec

private[sbt] trait UITask extends Runnable with AutoCloseable {
  private[sbt] def channel: CommandChannel
  private[sbt] def reader: UITask.Reader
  private[this] final def handleInput(s: Either[String, String]): Boolean = s match {
    case Left(m)    => channel.onFastTrackTask(m)
    case Right(cmd) => channel.onCommand(cmd)
  }
  private[this] val isStopped = new AtomicBoolean(false)
  override def run(): Unit = {
    @tailrec def impl(): Unit = {
      val res = reader.readLine()
      if (!handleInput(res) && !isStopped.get) impl()
    }
    try impl()
    catch { case _: InterruptedException | _: ClosedChannelException => isStopped.set(true) }
  }
  override def close(): Unit = isStopped.set(true)
}

private[sbt] object UITask {
  trait Reader { def readLine(): Either[String, String] }
  object Reader {
    def terminalReader(parser: Parser[_])(
        terminal: Terminal,
        state: State
    ): Reader = {
      val lineReader = LineReader.createReader(history(state), terminal, terminal.prompt)
      JLineCompletion.installCustomCompletor(lineReader, parser)
      () => {
        val clear = terminal.ansi(ClearPromptLine, "")
        try {
          @tailrec def impl(): Either[String, String] = {
            lineReader.readLine(clear + terminal.prompt.mkPrompt()) match {
              case null if terminal == Terminal.console && System.console == null =>
                // No stdin is attached to the process so just ignore the result and
                // block until the thread is interrupted.
                this.synchronized(this.wait())
                Right("") // should be unreachable
              // JLine returns null on ctrl+d when there is no other input. This interprets
              // ctrl+d with no imput as an exit
              case null => Left(TerminateAction)
              case s: String =>
                lineReader.getHistory match {
                  case p: PersistentHistory =>
                    p.add(s)
                    p.flush()
                  case _ =>
                }
                s match {
                  case ""                                                => impl()
                  case cmd @ (`Shutdown` | `TerminateAction` | `Cancel`) => Left(cmd)
                  case cmd =>
                    if (terminal.prompt != Prompt.Batch) terminal.setPrompt(Prompt.Running)
                    terminal.printStream.write(Int.MinValue)
                    Right(cmd)
                }
            }
          }
          impl()
        } catch {
          case _: InterruptedException => Right("")
        } finally lineReader.close()
      }
    }
  }
  private[this] def history(s: State): Option[File] =
    s.get(historyPath).getOrElse(Some(new File(s.baseDir, ".history")))
  private[sbt] def shellPrompt(terminal: Terminal, s: State): String =
    s.get(terminalShellPrompt) match {
      case Some(pf) => pf(terminal, s)
      case None =>
        def ansi(s: String): String = if (terminal.isAnsiSupported) s"$s" else ""
        s"${ansi(DeleteLine)}> ${ansi(ClearScreenAfterCursor)}"
    }
  private[sbt] class AskUserTask(
      state: State,
      override val channel: CommandChannel,
  ) extends UITask {
    override private[sbt] def reader: UITask.Reader = {
      UITask.Reader.terminalReader(state.combinedParser)(channel.terminal, state)
    }
  }
}
