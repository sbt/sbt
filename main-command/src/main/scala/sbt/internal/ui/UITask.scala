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
import sbt.BasicKeys.{ historyPath, terminalShellPrompt }
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
  trait Reader extends AutoCloseable {
    def readLine(): Either[String, String]
    override def close(): Unit = {}
  }
  object Reader {
    def terminalReader(parser: Parser[_])(
        terminal: Terminal,
        state: State
    ): Reader = new Reader {
      val closed = new AtomicBoolean(false)
      def readLine(): Either[String, String] =
        try {
          val clear = terminal.ansi(ClearPromptLine, "")
          @tailrec def impl(): Either[String, String] = {
            val thread = Thread.currentThread
            if (thread.isInterrupted || closed.get) throw new InterruptedException
            val reader = LineReader.createReader(history(state), parser, terminal)
            if (thread.isInterrupted || closed.get) throw new InterruptedException
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
                  case ""                                                => impl()
                  case cmd @ (`Shutdown` | `TerminateAction` | `Cancel`) => Left(cmd)
                  case cmd                                               => Right(cmd)
                }
            }
          }
          impl()
        } catch { case e: InterruptedException => Right("") }
      override def close(): Unit = closed.set(true)
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
    override private[sbt] lazy val reader: UITask.Reader = {
      UITask.Reader.terminalReader(state.combinedParser)(channel.terminal, state)
    }
  }
}
