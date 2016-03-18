package sbt

import sbt.internal.util._
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicReference }
import BasicKeys._
import java.io.File

private[sbt] final class ConsoleListener(queue: ConcurrentLinkedQueue[(String, Option[String])]) extends CommandListener(queue) {
  private var askUserThread: Option[Thread] = None
  def makeAskUserThread(status: CommandStatus): Thread = new Thread("ask-user-thread") {
    val s = status.state
    val history = (s get historyPath) getOrElse Some(new File(s.baseDir, ".history"))
    val prompt = (s get shellPrompt) match { case Some(pf) => pf(s); case None => "> " }
    // val reader = new FullReader(history, s.combinedParser)
    // val line = reader.readLine(prompt)
    // line match {
    //   case Some(line) =>
    //     val newState = s.copy(onFailure = Some(Shell), remainingCommands = line +: Shell +: s.remainingCommands).setInteractive(true)
    //     if (line.trim.isEmpty) newState else newState.clearGlobalLog
    //   case None => s.setInteractive(false)
    // }
    val reader = JLine.simple(None, false)
    override def run(): Unit = {
      try {
        val line = reader.readLine(prompt)
        line map { x => queue.add(("human", Some(x))) }
      } catch {
        case e: InterruptedException =>
      }
    }
  }

  def run(status: CommandStatus): Unit =
    askUserThread match {
      case Some(x) if x.isAlive => //
      case _ =>
        val x = makeAskUserThread(status)
        x.start
        askUserThread = Some(x)
    }

  def shutdown(): Unit =
    askUserThread match {
      case Some(x) if x.isAlive =>
        x.interrupt
        askUserThread = None
        println("shutdown ask user thread")
      case _ => ()
    }

  def pause(): Unit = shutdown()

  def resume(status: CommandStatus): Unit =
    askUserThread match {
      case Some(x) if x.isAlive => //
        println("resume??")
      case _ =>
        val x = makeAskUserThread(status)
        println("resume")
        x.start
        askUserThread = Some(x)
    }

  def setStatus(status: CommandStatus): Unit = ()
}
