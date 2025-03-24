/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

package ui

import java.util.concurrent.atomic.{ AtomicBoolean, AtomicReference }
import java.util.concurrent.Executors

import sbt.State
import scala.concurrent.duration.*
import sbt.internal.util.JoinThread.*
import sbt.internal.util.{ ConsoleAppender, ProgressEvent, ProgressState, Prompt }

private[sbt] class UserThread(val channel: CommandChannel) extends AutoCloseable {
  private val uiThread = new AtomicReference[(UITask, Thread)]
  private[sbt] final def onProgressEvent(pe: ProgressEvent): Unit = {
    lastProgressEvent.set(pe)
    ProgressState.updateProgressState(pe, channel.terminal)
  }
  private val executor =
    Executors.newSingleThreadExecutor(r => new Thread(r, s"sbt-$name-ui-thread"))
  private val lastProgressEvent = new AtomicReference[ProgressEvent]
  private val isClosed = new AtomicBoolean(false)

  private[sbt] def reset(state: State): Unit = if (!isClosed.get) {
    uiThread.synchronized {
      val task = channel.makeUIThread(state)
      def submit(): Unit = {
        val thread: Thread = new Thread(s"sbt-$name-ui-thread") {
          setDaemon(true)
          override def run(): Unit =
            try task.run()
            finally {
              uiThread.getAndSet(null) match {
                case prev @ (_, th) if th != this => uiThread.set(prev)
                case _                            =>
              }
            }
        }
        uiThread.getAndSet((task, thread)) match {
          case null => thread.start()
          case (prevTask, prevThread) if prevTask.getClass != task.getClass =>
            prevTask.close()
            prevThread.joinFor(1.second)
            thread.start()
          case t => uiThread.set(t)
        }
      }
      uiThread.get match {
        case null                                                => submit()
        case (prevTask, _) if prevTask.getClass == task.getClass =>
        case (t, thread) =>
          stopThreadImpl()
          submit()
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
      if (terminal.isAnsiSupported) {
        channel.terminal.withPrintStream { ps =>
          ps.print(ConsoleAppender.ClearScreenAfterCursor)
          ps.flush()
        }
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
