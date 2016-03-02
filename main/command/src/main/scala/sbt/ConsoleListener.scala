package sbt

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicReference }

class ConsoleListener extends CommandListener {

  // val history = (s get historyPath) getOrElse Some(new File(s.baseDir, ".history"))
  // val prompt = (s get shellPrompt) match { case Some(pf) => pf(s); case None => "> " }
  // val reader = new FullReader(history, s.combinedParser)
  // val line = reader.readLine(prompt)
  // line match {
  //   case Some(line) =>
  //     val newState = s.copy(onFailure = Some(Shell), remainingCommands = line +: Shell +: s.remainingCommands).setInteractive(true)
  //     if (line.trim.isEmpty) newState else newState.clearGlobalLog
  //   case None => s.setInteractive(false)
  // }

  def run(queue: ConcurrentLinkedQueue[Option[String]],
    status: CommandStatus): Unit =
    {
      // spawn thread and loop
    }

  def shutdown(): Unit =
    {
      // interrupt and kill the thread
    }

  def setStatus(status: CommandStatus): Unit = ???
}
