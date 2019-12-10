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

import sbt.BasicKeys._
import sbt.internal.util.Util.AnyOps
import sbt.internal.util._
import sbt.protocol.EventMessage
import sjsonnew.JsonFormat

private[sbt] final class ConsoleChannel(val name: String) extends CommandChannel {
  private var askUserThread: Option[Thread] = None
  def makeAskUserThread(s: State): Thread = new Thread("ask-user-thread") {
    val history = (s get historyPath) getOrElse (new File(s.baseDir, ".history")).some
    val prompt = (s get shellPrompt) match {
      case Some(pf) => pf(s)
      case None =>
        def ansi(s: String): String = if (ConsoleAppender.formatEnabledInEnv) s"$s" else ""
        s"${ansi(ConsoleAppender.DeleteLine)}> ${ansi(ConsoleAppender.clearScreen(0))}"
    }
    val reader =
      new FullReader(history, s.combinedParser, JLine.HandleCONT, Terminal.throwOnClosedSystemIn)
    override def run(): Unit = {
      // This internally handles thread interruption and returns Some("")
      try {
        reader.readLine(prompt) match {
          case Some(cmd) => append(Exec(cmd, Some(Exec.newExecId), Some(CommandSource(name))))
          case None =>
            println("") // Prevents server shutdown log lines from appearing on the prompt line
            append(Exec("exit", Some(Exec.newExecId), Some(CommandSource(name))))
        }
      } catch {
        case _: ClosedChannelException =>
      }
      askUserThread = None
    }
  }

  def run(s: State): State = s

  def publishBytes(bytes: Array[Byte]): Unit = ()

  def publishEvent[A: JsonFormat](event: A, execId: Option[String]): Unit = ()

  def publishEventMessage(event: EventMessage): Unit =
    event match {
      case e: ConsolePromptEvent =>
        askUserThread match {
          case Some(_) =>
          case _ =>
            val x = makeAskUserThread(e.state)
            askUserThread = Some(x)
            x.start()
        }
      case _ => //
    }

  def shutdown(): Unit =
    askUserThread match {
      case Some(x) if x.isAlive =>
        x.interrupt()
        askUserThread = None
      case _ => ()
    }
}
