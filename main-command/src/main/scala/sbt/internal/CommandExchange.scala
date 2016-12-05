package sbt
package internal

import java.net.SocketException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import sbt.internal.server._
import sbt.protocol.Serialization
import scala.collection.mutable.ListBuffer
import scala.annotation.tailrec
import BasicKeys.serverPort
import sbt.protocol.StatusEvent
import java.net.Socket

/**
 * The command exchange merges multiple command channels (e.g. network and console),
 * and acts as the central multiplexing point.
 * Instead of blocking on JLine.readLine, the server commmand will block on
 * this exchange, which could serve command request from either of the channel.
 */
private[sbt] final class CommandExchange {
  private val lock = new AnyRef {}
  private var server: Option[ServerInstance] = None
  private val commandQueue: ConcurrentLinkedQueue[Exec] = new ConcurrentLinkedQueue()
  private val channelBuffer: ListBuffer[CommandChannel] = new ListBuffer()
  private val nextChannelId: AtomicInteger = new AtomicInteger(0)
  def channels: List[CommandChannel] = channelBuffer.toList
  def subscribe(c: CommandChannel): Unit =
    lock.synchronized {
      channelBuffer.append(c)
    }

  subscribe(new ConsoleChannel())

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

  def run(s: State): State = runServer(s)

  private def newChannelName: String = s"channel-${nextChannelId.incrementAndGet()}"

  private def runServer(s: State): State =
    {
      val port = (s get serverPort) match {
        case Some(x) => x
        case None    => 5001
      }
      def onIncomingSocket(socket: Socket): Unit =
        {
          s.log.info(s"new client connected from: ${socket.getPort}")
          val channel = new NetworkChannel(newChannelName, socket)
          subscribe(channel)
        }
      server match {
        case Some(x) => // do nothing
        case _ =>
          server = Some(Server.start("127.0.0.1", port, onIncomingSocket, s.log))
      }
      s
    }

  def shutdown(): Unit =
    {
      channels foreach { c =>
        c.shutdown()
      }
      // interrupt and kill the thread
      server.foreach(_.shutdown())
      server = None
    }

  // fanout publishStatus to all channels
  def publishStatus(status: CommandStatus, lastSource: Option[CommandSource]): Unit =
    {
      val toDel: ListBuffer[CommandChannel] = ListBuffer.empty

      val event =
        if (status.canEnter) StatusEvent("Ready", Vector())
        else StatusEvent("Processing", status.state.remainingCommands.toVector)

      // TODO do not do this on the calling thread
      val bytes = Serialization.serializeEvent(event)
      channels.foreach {
        case c: ConsoleChannel =>
          c.publishStatus(status, lastSource)
        case c: NetworkChannel =>
          try {
            c.publishBytes(bytes)
          } catch {
            case e: SocketException =>
              // log.debug(e.getMessage)
              toDel += c
          }
      }
      toDel.toList match {
        case Nil => // do nothing
        case xs =>
          lock.synchronized {
            channelBuffer --= xs
          }
      }
    }
}
