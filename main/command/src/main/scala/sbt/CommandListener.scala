package sbt

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

trait CommandListener {
  // represents a loop that keeps asking an IO device for String input
  def run(queue: ConcurrentLinkedQueue[Option[String]],
    status: CommandStatus): Unit
  def shutdown(): Unit
  def setStatus(status: CommandStatus): Unit
}
