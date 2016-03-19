package sbt
package internal

import sbt.internal.server._
import BasicKeys._

private[sbt] final class NetworkChannel(exchange: CommandExchange) extends CommandChannel(exchange) {
  private var server: Option[ServerInstance] = None

  def runOrResume(status: CommandStatus): Unit =
    {
      val s = status.state
      val port = (s get serverPort) match {
        case Some(x) => x
        case None    => 5001
      }
      def onCommand(command: internal.server.Command): Unit = {
        command match {
          case Execution(cmd) => exchange.append(CommandRequest(CommandSource.Network, cmd))
        }
      }
      server match {
        case Some(x) => // do nothing
        case _ =>
          server = Some(Server.start("127.0.0.1", port, onCommand))
      }
    }

  def shutdown(): Unit =
    {
      // interrupt and kill the thread
      server.foreach(_.shutdown())
      server = None
    }

  // network doesn't pause or resume
  def pause(): Unit = ()

  // network doesn't pause or resume
  def resume(status: CommandStatus): Unit = ()

  def setStatus(cmdStatus: CommandStatus, lastSource: Option[CommandSource]): Unit = {
    server.foreach(server =>
      server.publish(
        if (cmdStatus.canEnter) StatusEvent(Ready)
        else StatusEvent(Processing("TODO current command", cmdStatus.state.remainingCommands))
      )
    )
  }
}
