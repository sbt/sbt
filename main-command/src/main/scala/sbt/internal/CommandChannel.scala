/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference

import sbt.internal.ui.{ UITask, UserThread }
import sbt.internal.util.Terminal
import sbt.protocol.EventMessage
import sbt.util.Level

import scala.jdk.CollectionConverters.*

/**
 * A command channel represents an IO device such as network socket or human
 * that can issue command or listen for some outputs.
 * We can think of a command channel to be an abstraction of the terminal window.
 */
abstract class CommandChannel {
  private val commandQueue: ConcurrentLinkedQueue[Exec] = new ConcurrentLinkedQueue()
  private val registered: java.util.Set[java.util.Queue[Exec]] = new java.util.HashSet
  private val fastTrack: java.util.Set[java.util.Queue[FastTrackTask]] = new java.util.HashSet
  private[sbt] final def register(
      queue: java.util.Queue[Exec],
      fastTrackQueue: java.util.Queue[FastTrackTask]
  ): Unit =
    registered.synchronized {
      registered.add(queue)
      if (!commandQueue.isEmpty) {
        queue.addAll(commandQueue)
        commandQueue.clear()
      }
      fastTrack.add(fastTrackQueue)
      ()
    }
  private[sbt] final def unregister(
      queue: java.util.Queue[CommandChannel],
      fastTrackQueue: java.util.Queue[FastTrackTask]
  ): Unit =
    registered.synchronized {
      registered.remove(queue)
      fastTrack.remove(fastTrackQueue)
      ()
    }
  private[sbt] final def addFastTrackTask(task: String): Unit = {
    fastTrack.forEach(q => q.synchronized { q.add(new FastTrackTask(this, task)); () })
  }
  private[sbt] def mkUIThread: (State, CommandChannel) => UITask
  private[sbt] def makeUIThread(state: State): UITask = mkUIThread(state, this)
  final def append(exec: Exec): Boolean = {
    registered.synchronized {
      exec.commandLine.nonEmpty && {
        if (registered.isEmpty) commandQueue.add(exec)
        else registered.asScala.forall(_.add(exec))
      }
    }
  }
  def poll: Option[Exec] = Option(commandQueue.poll)

  def prompt(e: ConsolePromptEvent): Unit = userThread.onConsolePromptEvent(e)
  def unprompt(e: ConsoleUnpromptEvent): Unit = userThread.onConsoleUnpromptEvent(e)
  def publishBytes(bytes: Array[Byte]): Unit
  private[sbt] def userThread: UserThread
  def shutdown(logShutdown: Boolean): Unit = {
    userThread.stopThread()
    userThread.close()
  }
  @deprecated("Use the variant that takes the logShutdown parameter", "1.4.0")
  def shutdown(): Unit = shutdown(true)
  def name: String
  private val level = new AtomicReference[Level.Value](Level.Info)
  private[sbt] final def setLevel(l: Level.Value): Unit = level.set(l)
  private[sbt] final def logLevel: Level.Value = level.get
  private def setLevel(value: Level.Value, cmd: String): Boolean = {
    level.set(value)
    append(Exec(cmd, Some(Exec.newExecId), Some(CommandSource(name))))
  }
  private[sbt] def onCommand: String => Boolean = {
    case "error" => setLevel(Level.Error, "error")
    case "debug" => setLevel(Level.Debug, "debug")
    case "info"  => setLevel(Level.Info, "info")
    case "warn"  => setLevel(Level.Warn, "warn")
    case cmd =>
      if (cmd.nonEmpty) append(Exec(cmd, Some(Exec.newExecId), Some(CommandSource(name))))
      else false
  }
  private[sbt] def onFastTrackTask: String => Boolean = { (s: String) =>
    fastTrack.synchronized(fastTrack.forEach { q =>
      q.add(new FastTrackTask(this, s))
      ()
    })
    true
  }

  private[sbt] def terminal: Terminal
  private[sbt] var _active: Boolean = true
  private[sbt] def pause(): Unit = _active = false
  private[sbt] def isPaused: Boolean = !_active
  private[sbt] def resume(): Unit = _active = true
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
case class ConsoleUnpromptEvent(lastSource: Option[CommandSource]) extends EventMessage

private[internal] class FastTrackTask(val channel: CommandChannel, val task: String)
