/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.util.concurrent.ConcurrentLinkedQueue

import sbt.internal.util.Terminal
import sbt.protocol.EventMessage
import scala.collection.JavaConverters._

/**
 * A command channel represents an IO device such as network socket or human
 * that can issue command or listen for some outputs.
 * We can think of a command channel to be an abstraction of the terminal window.
 */
abstract class CommandChannel {
  private val commandQueue: ConcurrentLinkedQueue[Exec] = new ConcurrentLinkedQueue()
  private val registered: java.util.Set[java.util.Queue[Exec]] = new java.util.HashSet
  private val maintenance: java.util.Set[java.util.Queue[MaintenanceTask]] = new java.util.HashSet
  private[sbt] final def register(
      queue: java.util.Queue[Exec],
      maintenanceQueue: java.util.Queue[MaintenanceTask]
  ): Unit =
    registered.synchronized {
      registered.add(queue)
      if (!commandQueue.isEmpty) {
        queue.addAll(commandQueue)
        commandQueue.clear()
      }
      maintenance.add(maintenanceQueue)
      ()
    }
  private[sbt] final def unregister(
      queue: java.util.Queue[CommandChannel],
      maintenanceQueue: java.util.Queue[MaintenanceTask]
  ): Unit =
    registered.synchronized {
      registered.remove(queue)
      maintenance.remove(maintenanceQueue)
      ()
    }
  private[sbt] final def initiateMaintenance(task: String): Unit = {
    maintenance.forEach(q => q.synchronized { q.add(new MaintenanceTask(this, task)); () })
  }
  final def append(exec: Exec): Boolean = {
    registered.synchronized {
      exec.commandLine.nonEmpty && {
        if (registered.isEmpty) commandQueue.add(exec)
        else registered.asScala.forall(_.add(exec))
      }
    }
  }
  def poll: Option[Exec] = Option(commandQueue.poll)

  def publishBytes(bytes: Array[Byte]): Unit
  def shutdown(): Unit
  def name: String
  private[sbt] def onCommand: String => Boolean = {
    case cmd =>
      if (cmd.nonEmpty) append(Exec(cmd, Some(Exec.newExecId), Some(CommandSource(name))))
      else false
  }
  private[sbt] def onMaintenance: String => Boolean = { s: String =>
    maintenance.synchronized(maintenance.forEach { q =>
      q.add(new MaintenanceTask(this, s))
      ()
    })
    true
  }

  private[sbt] def terminal: Terminal
}

// case class Exec(commandLine: String, source: Option[CommandSource])

// case class CommandSource(channelName: String)

/*
 * This is a data passed specifically for local prompting console.
 */
case class ConsolePromptEvent(state: State) extends EventMessage

/*
 * This is a data passed specifically for unprompting local console.
 */
@deprecated("No longer used", "1.4.0")
case class ConsoleUnpromptEvent(lastSource: Option[CommandSource]) extends EventMessage

private[internal] class MaintenanceTask(val channel: CommandChannel, val task: String)
