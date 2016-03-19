package sbt
package internal

import java.util.concurrent.ConcurrentLinkedQueue

import sbt.internal.server._

private[sbt] final class NetworkListener(queue: ConcurrentLinkedQueue[(String, Option[String])]) extends CommandListener(queue) {

  private var server: Option[ServerInstance] = None

  def run(status: CommandStatus): Unit =
    {
      def onCommand(command: internal.server.Command): Unit = {
        command match {
          case Execution(cmd) => queue.add(("network", Some(cmd)))
        }
      }

      server = Some(Server.start("127.0.0.1", 12700, onCommand))
    }

  def shutdown(): Unit =
    {
      // interrupt and kill the thread
      server.foreach(_.shutdown())
    }

  // network doesn't pause or resume
  def pause(): Unit = ()

  // network doesn't pause or resume
  def resume(status: CommandStatus): Unit = ()

  def setStatus(cmdStatus: CommandStatus): Unit = {
    server.foreach(server =>
      server.publish(
        if (cmdStatus.canEnter) StatusEvent(Ready)
        else StatusEvent(Processing("TODO current command", cmdStatus.state.remainingCommands))
      )
    )
  }
}
