package sbt
package internal

import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * The command exchange merges multiple command channels (e.g. network and console),
 * and acts as the central multiplexing point.
 * Instead of blocking on JLine.readLine, the server commmand will block on
 * this exchange, which could serve command request from either of the channel.
 */
private[sbt] final class CommandExchange {
  private val commandQueue: ConcurrentLinkedQueue[Exec] = new ConcurrentLinkedQueue()
  private val channelBuffer: ListBuffer[CommandChannel] = new ListBuffer()
  def channels: List[CommandChannel] = channelBuffer.toList
  def subscribe(c: CommandChannel): Unit =
    channelBuffer.append(c)

  subscribe(new ConsoleChannel())
  subscribe(new NetworkChannel())

  // periodically move all messages from all the channels
  @tailrec def blockUntilNextExec: Exec =
    {
      @tailrec def slurpMessages(): Unit =
        (((None: Option[Exec]) /: channels) { _ orElse _.poll }) match {
          case Some(x) =>
            commandQueue.add(x)
            slurpMessages
          case _ => ()
        }
      slurpMessages()
      Option(commandQueue.poll) match {
        case Some(x) => x
        case _ =>
          Thread.sleep(50)
          blockUntilNextExec
      }
    }

  // fanout run to all channels
  def run(s: State): State =
    (s /: channels) { (acc, c) => c.run(acc) }

  // fanout publishStatus to all channels
  def publishStatus(status: CommandStatus, lastSource: Option[CommandSource]): Unit =
    channels foreach { c =>
      c.publishStatus(status, lastSource)
    }
}
