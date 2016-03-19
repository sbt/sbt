package sbt
package internal

import java.util.concurrent.ConcurrentLinkedQueue
import scala.annotation.tailrec

/**
 * The command exchange merges multiple command channels (e.g. network and console),
 * and acts as the central multiplexing point.
 * Instead of blocking on JLine.readLine, the server commmand will block on
 * this exchange, which could serve command request from either of the channel.
 */
private[sbt] final class CommandExchange {
  private val commandQueue: ConcurrentLinkedQueue[CommandRequest] = new ConcurrentLinkedQueue()
  lazy val channels = Seq(new ConsoleChannel(this), new NetworkChannel(this))

  def append(request: CommandRequest): Boolean =
    commandQueue.add(request)

  @tailrec def blockUntilNextCommand: CommandRequest =
    Option(commandQueue.poll) match {
      case Some(x) => x
      case _ =>
        Thread.sleep(50)
        blockUntilNextCommand
    }
}
