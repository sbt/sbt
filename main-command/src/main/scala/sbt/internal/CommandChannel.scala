package sbt
package internal

import java.util.concurrent.ConcurrentLinkedQueue
import sbt.protocol.EventMessage

/**
 * A command channel represents an IO device such as network socket or human
 * that can issue command or listen for some outputs.
 * We can think of a command channel to be an abstration of the terminal window.
 */
abstract class CommandChannel {
  private val commandQueue: ConcurrentLinkedQueue[Exec] = new ConcurrentLinkedQueue()
  def append(exec: Exec): Boolean =
    commandQueue.add(exec)
  def poll: Option[Exec] = Option(commandQueue.poll)

  def publishEvent(event: EventMessage): Unit
  def publishBytes(bytes: Array[Byte]): Unit
  def shutdown(): Unit
}

case class Exec(commandLine: String, source: Option[CommandSource])

case class CommandSource(channelName: String)

/*
 * This is a data passed specifically for local prompting console.
 */
case class ConsolePromptEvent(state: State) extends EventMessage

/*
 * This is a data passed specifically for unprompting local console.
 */
case class ConsoleUnpromptEvent(lastSource: Option[CommandSource]) extends EventMessage
