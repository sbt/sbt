package sbt
package internal

import java.util.concurrent.ConcurrentLinkedQueue

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

  def publishStatus(status: CommandStatus, lastSource: Option[CommandSource]): Unit
  def publishBytes(bytes: Array[Byte]): Unit
  def shutdown(): Unit
}

case class Exec(source: CommandSource, commandLine: String)

sealed trait CommandSource
object CommandSource {
  case object Human extends CommandSource
  case object Network extends CommandSource
}

/**
 * This is a data that is passed on to the channels.
 * The canEnter paramter indicates that the console devise or UI
 * should stop listening.
 */
case class CommandStatus(state: State, canEnter: Boolean)
