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

import sbt.BasicCommandStrings.{ Cancel, TerminateAction, Shutdown }
import sbt.BasicKeys.{ historyPath, colorShellPrompt }
import sbt.State
import sbt.internal.CommandChannel
import sbt.internal.util.ConsoleAppender.{ ClearPromptLine, ClearScreenAfterCursor, DeleteLine }
import sbt.internal.util._
import sbt.internal.util.complete.{ Parser }

import scala.annotation.tailrec

private[sbt] trait UITask extends Runnable with AutoCloseable {
  private[sbt] val channel: CommandChannel
  private[sbt] val reader: UITask.Reader
  private[this] final def handleInput(s: Either[String, String]): Boolean = s match {
    case Left(m)    => channel.onFastTrackTask(m)
    case Right(cmd) => channel.onCommand(cmd)
  }
  private[this] val isStopped = new AtomicBoolean(false)
  override def run(): Unit = {
    @tailrec def impl(): Unit = if (!isStopped.get) {
      val res = reader.readLine()
      if (!handleInput(res) && !isStopped.get) impl()
    }
    try impl()
    catch { case _: InterruptedException | _: ClosedChannelException => isStopped.set(true) }
  }
  override def close(): Unit = {
    isStopped.set(true)
    reader.close()
  }
}

private[sbt] object UITask {
  case object NoShellPrompt extends (State => String) {
    override def apply(state: State): String = ""
  }
  trait Reader extends AutoCloseable {
    def readLine(): Either[String, String]
    override def close(): Unit = {}
  }
  object Reader {
    // Avoid filling the stack trace since it isn't helpful here
    object interrupted extends InterruptedException
    def terminalReader(parser: Parser[_])(
        terminal: Terminal,
        state: State
    ): Reader = new Reader {
      val closed = new AtomicBoolean(false)
      def readLine(): Either[String, String] =
        try {
          val clear = terminal.ansi(ClearPromptLine, "")
          val res = {
            val thread = Thread.currentThread
            if (thread.isInterrupted || closed.get) throw interrupted
            val reader = LineReader.createReader(history(state), parser, terminal)
            if (thread.isInterrupted || closed.get) throw interrupted
            (try reader.readLine(clear + terminal.prompt.mkPrompt())
            finally reader.close) match {
              case None if terminal == Terminal.console && System.console == null =>
                // No stdin is attached to the process so just ignore the result and
                // block until the thread is interrupted.
                this.synchronized(this.wait())
                Right("") // should be unreachable
              // JLine returns null on ctrl+d when there is no other input. This interprets
              // ctrl+d with no imput as an exit
              case None => Left(TerminateAction)
              case Some(s: String) =>
                s.trim() match {
                  // We need to put the empty string on the fast track queue so that we can
                  // reprompt the user if another command is running on the server.
                  case ""                                                => Left("")
                  case cmd @ (`Shutdown` | `TerminateAction` | `Cancel`) => Left(cmd)
                  case cmd                                               => Right(cmd)
                }
            }
          }
          terminal.setPrompt(Prompt.Pending)
          res
        } catch { case e: InterruptedException => Left("") }
      override def close(): Unit = closed.set(true)
    }
  }
  private[this] def history(s: State): Option[File] =
    s.get(historyPath).getOrElse(Some(new File(s.baseDir, ".history")))
  private[sbt] def shellPrompt(terminal: Terminal, s: State): String =
    s.get(sbt.BasicKeys.shellPrompt) match {
      case Some(NoShellPrompt) | None =>
        s.get(colorShellPrompt) match {
          case Some(pf) => pf(terminal.isColorEnabled, s)
          case None =>
            def color(s: String): String = if (terminal.isColorEnabled) s"$s" else ""
            s"${color(DeleteLine)}> ${color(ClearScreenAfterCursor)}"
        }
      case Some(p) => p(s)
    }
  private[sbt] class AskUserTask(
      state: State,
      override val channel: CommandChannel,
  ) extends UITask {
    override private[sbt] lazy val reader: UITask.Reader = {
      UITask.Reader.terminalReader(state.combinedParser)(channel.terminal, state)
    }
  }
  private[sbt] class BlockedTerminalTask(override val channel: CommandChannel) extends UITask {
    override private[sbt] lazy val reader: UITask.Reader = { () =>
      {
        channel.terminal.prompt match {
          case b: Prompt.Blocked => b.join()
          case _                 =>
        }
        Right("")
      }
    }
  }
}
