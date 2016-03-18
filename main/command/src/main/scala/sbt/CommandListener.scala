package sbt

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

abstract class CommandListener(queue: ConcurrentLinkedQueue[(String, Option[String])]) {
  // represents a loop that keeps asking an IO device for String input
  def run(status: CommandStatus): Unit
  def shutdown(): Unit
  def setStatus(status: CommandStatus): Unit
  def pause(): Unit
  def resume(status: CommandStatus): Unit
}
