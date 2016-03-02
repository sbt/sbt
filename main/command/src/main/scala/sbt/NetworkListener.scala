package sbt

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

import server.Server

class NetworkListener extends CommandListener {
  def run(queue: ConcurrentLinkedQueue[Option[String]],
    status: CommandStatus): Unit =
    {
      val server = Server.start("127.0.0.1", 12700)
      // spawn thread and loop
    }

  def shutdown(): Unit =
    {
      // interrupt and kill the thread
    }

  def setStatus(status: CommandStatus): Unit = ???
}
