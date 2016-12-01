package sbt
package internal

import sbt.internal.server._
import sbt.protocol._
import BasicKeys._

private[sbt] final class NetworkChannel extends CommandChannel {
  private var server: Option[ServerInstance] = None

  def run(s: State): State =
    {
      val port = (s get serverPort) match {
        case Some(x) => x
        case None    => 5001
      }
      def onCommand(command: CommandMessage): Unit =
        command match {
          case x: ExecCommand => append(Exec(CommandSource.Network, x.commandLine))
        }
      server match {
        case Some(x) => // do nothing
        case _ =>
          server = Some(Server.start("127.0.0.1", port, onCommand, s.log))
      }
      s
    }

  def shutdown(): Unit =
    {
      // interrupt and kill the thread
      server.foreach(_.shutdown())
      server = None
    }

  def publishStatus(cmdStatus: CommandStatus, lastSource: Option[CommandSource]): Unit = {
    server.foreach(server =>
      server.publish(
        if (cmdStatus.canEnter) StatusEvent("Ready", Vector())
        else StatusEvent("Processing", cmdStatus.state.remainingCommands.toVector)
      ))
  }
}
