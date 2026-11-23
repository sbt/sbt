/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.ui

import java.io.{ File, IOException }
import java.nio.channels.ClosedChannelException
import java.nio.file.{ Files, Path }
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission.{ OWNER_EXECUTE, OWNER_READ, OWNER_WRITE }
import java.util.EnumSet
import java.util.concurrent.atomic.AtomicBoolean

import sbt.BasicCommandStrings.{ Cancel, TerminateAction, Shutdown }
import sbt.BasicKeys.{ historyPath, colorShellPrompt, rootOutputDirectory }
import sbt.State
import sbt.internal.CommandChannel
import sbt.internal.util.ConsoleAppender.{ ClearPromptLine, ClearScreenAfterCursor, DeleteLine }
import sbt.internal.util.Terminal.hasConsole
import sbt.internal.util.*
import sbt.internal.util.complete.Parser

import scala.annotation.tailrec
import scala.util.Try

private[sbt] trait UITask extends Runnable with AutoCloseable:
  private[sbt] val channel: CommandChannel
  private[sbt] def reader: UITask.Reader
  private final def handleInput(s: Either[String, String]): Boolean = s match
    case Left(m)    => channel.onFastTrackTask(m)
    case Right(cmd) => channel.onCommandLine(cmd)
  private val isStopped = new AtomicBoolean(false)
  override def run(): Unit =
    @tailrec def impl(): Unit = if !isStopped.get then
      val res = reader.readLine()
      if !handleInput(res) && !isStopped.get then impl()
    try impl()
    catch case _: InterruptedException | _: ClosedChannelException => isStopped.set(true)
  override def close(): Unit =
    isStopped.set(true)
    reader.close()

private[sbt] object UITask:
  case object NoShellPrompt extends (State => String):
    override def apply(state: State): String = ""
  trait Reader extends AutoCloseable:
    def readLine(): Either[String, String]
    override def close(): Unit = {}
  object Reader:
    // Avoid filling the stack trace since it isn't helpful here
    object interrupted extends InterruptedException

    /**
     * Return Left for fast track commands, otherwise return Right(...).
     */
    def splitCommand(cmd: String): Either[String, String] =
      // We need to put the empty string on the fast track queue so that we can
      // reprompt the user if another command is running on the server.
      if cmd.isEmpty() then Left("")
      else
        cmd match
          case Shutdown | TerminateAction | Cancel => Left(cmd)
          case cmd                                 => Right(cmd)

    def terminalReader(parser: Parser[?])(
        terminal: Terminal,
        state: State
    ): Reader = new Reader:
      val closed = new AtomicBoolean(false)
      private lazy val historyFile = history(state)
      def readLine(): Either[String, String] =
        try
          val clear = terminal.ansi(ClearPromptLine, "")
          val res =
            val thread = Thread.currentThread
            if thread.isInterrupted || closed.get then throw interrupted
            val reader = LineReader.createReader(historyFile, parser, terminal)
            if thread.isInterrupted || closed.get then throw interrupted
            (try reader.readLine(clear + terminal.prompt.mkPrompt())
            finally reader.close) match
              case None if terminal == Terminal.console && !hasConsole =>
                // No stdin is attached to the process so just ignore the result and
                // block until the thread is interrupted.
                this.synchronized(this.wait())
                Right("") // should be unreachable
              // JLine returns null on ctrl+d when there is no other input. This interprets
              // ctrl+d with no imput as an exit
              case None            => Left(TerminateAction)
              case Some(s: String) => splitCommand(s.trim())
          terminal.setPrompt(Prompt.Pending)
          res
        catch case e: InterruptedException => Left("")
      override def close(): Unit = closed.set(true)
  end Reader
  private def history(s: State): Option[File] =
    val configured = s.get(historyPath)
    val file = configured.getOrElse(Some(new File(s.baseDir, ".history")))
    file.foreach: f =>
      Try(f.toPath).toOption.foreach: path =>
        if configured.isEmpty || s.get(rootOutputDirectory).exists(_.resolve(".history") == path)
        then restrictHistoryPermissions(path)
    file

  private[ui] def restrictHistoryPermissions(path: Path): Unit =
    try
      Option(Files.getFileAttributeView(path, classOf[PosixFileAttributeView], NOFOLLOW_LINKS))
        .foreach: view =>
          val attributes = view.readAttributes()
          if attributes.isRegularFile then
            val permissions = attributes.permissions()
            if permissions.retainAll(EnumSet.of(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE)) then
              view.setPermissions(permissions)
    catch case _: IOException | _: UnsupportedOperationException | _: SecurityException => ()

  private[sbt] def shellPrompt(terminal: Terminal, s: State): String =
    s.get(sbt.BasicKeys.shellPrompt) match
      case Some(NoShellPrompt) | None =>
        s.get(colorShellPrompt) match
          case Some(pf) => pf(terminal.isColorEnabled, s)
          case None     =>
            def color(s: String): String = if terminal.isColorEnabled then s"$s" else ""
            s"${color(DeleteLine)}> ${color(ClearScreenAfterCursor)}"
      case Some(p) => p(s)
  private[sbt] class AskUserTask(
      state: State,
      override val channel: CommandChannel,
  ) extends UITask:
    override private[sbt] lazy val reader: UITask.Reader =
      UITask.Reader.terminalReader(state.combinedParser)(channel.terminal, state)
end UITask
