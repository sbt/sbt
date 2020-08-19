/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

package ui

import java.util.concurrent.atomic.{ AtomicBoolean, AtomicReference }
import java.util.concurrent.Executors

import sbt.State
import scala.concurrent.duration._
import sbt.internal.util.JoinThread._
import sbt.internal.util.{ ConsoleAppender, ProgressEvent, ProgressState, Prompt }

private[sbt] class UserThread(val channel: CommandChannel) extends AutoCloseable {
  private[this] val uiThread = new AtomicReference[(UITask, Thread)]
  private[sbt] final def onProgressEvent(pe: ProgressEvent): Unit = {
    lastProgressEvent.set(pe)
    ProgressState.updateProgressState(pe, channel.terminal)
  }
  private[this] val executor =
    Executors.newSingleThreadExecutor(r => new Thread(r, s"sbt-$name-ui-thread"))
  private[this] val lastProgressEvent = new AtomicReference[ProgressEvent]
  private[this] val isClosed = new AtomicBoolean(false)

  private[sbt] def reset(state: State): Unit = if (!isClosed.get) {
    uiThread.synchronized {
      val task = channel.makeUIThread(state)
      def submit(): Thread = {
        val thread: Thread = new Thread(s"sbt-$name-ui-thread") {
          setDaemon(true)
          override def run(): Unit =
            try task.run()
            finally uiThread.get match {
              case (_, t) if t == this => uiThread.set(null)
              case _                   =>
            }
        }
        uiThread.getAndSet((task, thread)) match {
          case null => thread.start()
          case (task, t) if t.getClass != task.getClass =>
            stopThreadImpl()
            thread.start()
          case t => uiThread.set(t)
        }
        thread
      }
      uiThread.get match {
        case null                                  => uiThread.set((task, submit()))
        case (t, _) if t.getClass == task.getClass =>
        case (t, thread) =>
          stopThreadImpl()
          uiThread.set((task, submit()))
      }
    }
    Option(lastProgressEvent.get).foreach(onProgressEvent)
  }

  private[sbt] def stopThreadImpl(): Unit = uiThread.synchronized {
    uiThread.getAndSet(null) match {
      case null =>
      case (t, thread) =>
        t.close()
        thread.joinFor(1.second)
    }
  }
  private[sbt] def stopThread(): Unit = uiThread.synchronized(stopThreadImpl())

  private[sbt] def onConsolePromptEvent(consolePromptEvent: ConsolePromptEvent): Unit =
    // synchronize to ensure that the state isn't modified during the call to reset
    // at the bottom
    synchronized {
      channel.terminal.withPrintStream { ps =>
        ps.print(ConsoleAppender.ClearScreenAfterCursor)
        ps.flush()
      }
      val state = consolePromptEvent.state
      terminal.prompt match {
        case Prompt.Running | Prompt.Pending =>
          terminal.setPrompt(Prompt.AskUser(() => UITask.shellPrompt(terminal, state)))
        case _ =>
      }
      onProgressEvent(ProgressEvent("Info", Vector(), None, None, None))
      reset(state)
    }

  private[sbt] def onConsoleUnpromptEvent(
      consoleUnpromptEvent: ConsoleUnpromptEvent
  ): Unit = {
    terminal.setPrompt(Prompt.Pending)
    if (consoleUnpromptEvent.lastSource.fold(true)(_.channelName != name)) {
      terminal.progressState.reset()
    } else stopThread()
  }

  override def close(): Unit = if (isClosed.compareAndSet(false, true)) executor.shutdown()
  private def terminal = channel.terminal
  private def name: String = channel.name
}
