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
    val reader = new FullReader(history, s.combinedParser, JLine.HandleCONT, true)
    override def run(): Unit = {
      // This internally handles thread interruption and returns Some("")
      val line = reader.readLine(prompt)
      line match {
        case Some(cmd) => append(Exec(CommandSource.Human, cmd))
        case None      => append(Exec(CommandSource.Human, "exit"))
      }
      askUserThread = None
    }
  }

  def run(s: State): State = s

  def publishStatus(status: CommandStatus, lastSource: Option[CommandSource]): Unit =
    if (status.canEnter) {
      askUserThread match {
        case Some(x) => //
        case _ =>
          val x = makeAskUserThread(status)
          askUserThread = Some(x)
          x.start
      }
    } else {
      lastSource match {
        case Some(src) if src != CommandSource.Human =>
          askUserThread match {
            case Some(x) =>
              shutdown()
            case _ =>
          }
        case _ =>
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
