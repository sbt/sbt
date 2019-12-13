/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.io.File
import java.nio.channels.ClosedChannelException
import java.util.concurrent.atomic.AtomicReference

import sbt.BasicKeys._
import sbt.internal.util._
import sbt.protocol.EventMessage
import sjsonnew.JsonFormat

private[sbt] final class ConsoleChannel(val name: String) extends CommandChannel {
  private[this] val askUserThread = new AtomicReference[AskUserThread]
  private[this] def getPrompt(s: State): String = s.get(shellPrompt) match {
    case Some(pf) => pf(s)
    case None =>
      def ansi(s: String): String = if (ConsoleAppender.formatEnabledInEnv) s"$s" else ""
      s"${ansi(ConsoleAppender.DeleteLine)}> ${ansi(ConsoleAppender.clearScreen(0))}"
  }
  private[this] class AskUserThread(s: State) extends Thread("ask-user-thread") {
    private val history = s.get(historyPath).getOrElse(Some(new File(s.baseDir, ".history")))
    private val prompt = getPrompt(s)
    private val reader =
      new FullReader(
        history,
        s.combinedParser,
        LineReader.HandleCONT,
        Terminal.throwOnClosedSystemIn
      )
    setDaemon(true)
    start()
    override def run(): Unit =
      try {
        reader.readLine(prompt) match {
          case Some(cmd) => append(Exec(cmd, Some(Exec.newExecId), Some(CommandSource(name))))
          case None =>
            println("") // Prevents server shutdown log lines from appearing on the prompt line
            append(Exec("exit", Some(Exec.newExecId), Some(CommandSource(name))))
        }
        ()
      } catch {
        case _: ClosedChannelException =>
      } finally askUserThread.synchronized(askUserThread.set(null))
    def redraw(): Unit = {
      System.out.print(ConsoleAppender.clearLine(0))
      reader.redraw()
      System.out.print(ConsoleAppender.clearScreen(0))
      System.out.flush()
    }
  }
  private[this] def makeAskUserThread(s: State): AskUserThread = new AskUserThread(s)

  def run(s: State): State = s

  def publishBytes(bytes: Array[Byte]): Unit = ()

  def publishEvent[A: JsonFormat](event: A, execId: Option[String]): Unit = ()

  def publishEventMessage(event: EventMessage): Unit =
    event match {
      case e: ConsolePromptEvent =>
        if (Terminal.systemInIsAttached) {
          askUserThread.synchronized {
            askUserThread.get match {
              case null => askUserThread.set(makeAskUserThread(e.state))
              case t    => t.redraw()
            }
          }
        }
      case _ => //
    }

  def shutdown(): Unit = askUserThread.synchronized {
    askUserThread.get match {
      case null =>
      case t if t.isAlive =>
        t.interrupt()
        askUserThread.set(null)
      case _ => ()
    }
  }
}
