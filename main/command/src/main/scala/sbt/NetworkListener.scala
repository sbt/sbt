package sbt

import java.util.concurrent.ConcurrentLinkedQueue

import sbt.server._

private[sbt] final class NetworkListener extends CommandListener {

  private var server: Option[ServerInstance] = None

  def run(queue: ConcurrentLinkedQueue[Option[String]],
    status: CommandStatus): Unit =
    {
      def onCommand(command: sbt.server.Command): Unit = {
        command match {
          case Execution(cmd) => queue.add(Some(cmd))
        }
      }

      server = Some(Server.start("127.0.0.1", 12700, onCommand))
    }

  def shutdown(): Unit =
    {
      // interrupt and kill the thread
      server.foreach(_.shutdown())
    }

  def setStatus(cmdStatus: CommandStatus): Unit = {
    server.foreach(server =>
      server.publish(
        if (cmdStatus.canEnter) StatusEvent(Ready)
        else StatusEvent(Processing("TODO current command", cmdStatus.state.remainingCommands))
      )
    )
  }
}
