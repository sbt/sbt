package sbt
package internal

import sbt.internal.util._
import BasicKeys._
import java.io.File

private[sbt] final class ConsoleChannel extends CommandChannel {
  private var askUserThread: Option[Thread] = None
  def makeAskUserThread(status: CommandStatus): Thread = new Thread("ask-user-thread") {
    val s = status.state
    val history = (s get historyPath) getOrElse Some(new File(s.baseDir, ".history"))
    val prompt = (s get shellPrompt) match {
      case Some(pf) => pf(s)
      case None     => "> "
    }
    val reader = new FullReader(history, s.combinedParser)
    override def run(): Unit = {
      try {
        val line = reader.readLine(prompt)
        line match {
          case Some(cmd) => append(Exec(CommandSource.Human, cmd))
          case None      => append(Exec(CommandSource.Human, "exit"))
        }
      } catch {
        case e: InterruptedException =>
      }
    }
  }

  def run(s: State): State = s

  def publishStatus(status: CommandStatus, lastSource: Option[CommandSource]): Unit =
    if (status.canEnter) {
      askUserThread match {
        case Some(x) if x.isAlive => //
        case _ =>
          val x = makeAskUserThread(status)
          x.start
          askUserThread = Some(x)
      }
    } else {
      shutdown()
      lastSource match {
        case Some(src) if src != CommandSource.Human =>
          val s = status.state
          s.remainingCommands.headOption map {
            System.out.println(_)
          }
        case _ => //
      }
    }

  def shutdown(): Unit =
    askUserThread match {
      case Some(x) if x.isAlive =>
        x.interrupt
        askUserThread = None
      case _ => ()
    }
}
