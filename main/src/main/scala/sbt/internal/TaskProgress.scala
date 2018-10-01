/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import sbt.internal.util.{ RMap, ConsoleOut }
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.{ blocking, Future, ExecutionContext }
import java.util.concurrent.atomic.AtomicBoolean
import scala.collection.JavaConverters._
import scala.collection.concurrent.TrieMap
import TaskProgress._

/**
 * implements task progress display on the shell.
 */
private[sbt] final class TaskProgress(currentRef: ProjectRef) extends ExecuteProgress[Task] {
  type S = Unit

  private[this] val showScopedKey = Def.showRelativeKey2(currentRef)
  // private[this] var start = 0L
  private[this] val activeTasks = new ConcurrentHashMap[Task[_], Long]
  private[this] val timings = new ConcurrentHashMap[Task[_], Long]
  private[this] val calledBy = new ConcurrentHashMap[Task[_], Task[_]]
  private[this] val anonOwners = new ConcurrentHashMap[Task[_], Task[_]]
  private[this] val isReady = new AtomicBoolean(false)
  private[this] val isAllCompleted = new AtomicBoolean(false)

  override def initial: Unit = ()
  override def registered(
      state: Unit,
      task: Task[_],
      allDeps: Iterable[Task[_]],
      pendingDeps: Iterable[Task[_]]
  ): Unit = {
    // we need this to infer anonymous task names
    pendingDeps foreach { t =>
      if (TaskName.transformNode(t).isEmpty) {
        anonOwners.put(t, task)
      }
    }
  }
  override def ready(state: Unit, task: Task[_]): Unit = {
    isReady.set(true)
  }

  override def workStarting(task: Task[_]): Unit = {
    activeTasks.put(task, System.nanoTime)
    ()
  }

  override def workFinished[A](task: Task[A], result: Either[Task[A], Result[A]]): Unit = {
    activeTasks.remove(task)
    timings.put(task, System.nanoTime - activeTasks.get(task))
    // we need this to infer anonymous task names
    result.left.foreach { t =>
      calledBy.put(t, task)
    }
  }

  override def completed[A](state: Unit, task: Task[A], result: Result[A]): Unit = ()
  override def allCompleted(state: Unit, results: RMap[Task, Result]): Unit = {
    isAllCompleted.set(true)
  }

  import ExecutionContext.Implicits._
  Future {
    while (!isReady.get) {
      blocking {
        Thread.sleep(500)
      }
    }
    readyLog()
    while (!isAllCompleted.get) {
      blocking {
        report()
        Thread.sleep(500)
      }
    }
  }

  private[this] val console = ConsoleOut.systemOut
  private[this] def readyLog(): Unit = {
    console.println("")
    console.println("")
    console.println("")
    console.println("")
    console.println("")
    console.println("")
    console.print(CursorUp5)
  }

  private[this] val stopReportTask =
    Set("run", "bgRun", "fgRun", "scala", "console", "consoleProject")
  private[this] def report(): Unit = console.lockObject.synchronized {
    val currentTasks = activeTasks.asScala.toList
    def report0: Unit = {
      console.print(s"$CursorDown1")
      currentTasks foreach {
        case (task, start) =>
          val elapsed = (System.nanoTime - start) / 1000000000L
          console.println(s"$DeleteLine1  | => ${taskName(task)} ${elapsed}s")
      }
      console.print(cursorUp(currentTasks.size + 1))
    }
    val isStop = currentTasks
      .map({ case (t, _) => taskName(t) })
      .exists(n => stopReportTask.exists(m => n.endsWith("/ " + m)))
    if (isStop) ()
    else report0
  }

  private[this] val taskNameCache = TrieMap.empty[Task[_], String]
  private[this] def taskName(t: Task[_]): String =
    taskNameCache.getOrElseUpdate(t, taskName0(t))
  private[this] def taskName0(t: Task[_]): String = {
    def definedName(node: Task[_]): Option[String] =
      node.info.name orElse TaskName.transformNode(node).map(showScopedKey.show)
    def inferredName(t: Task[_]): Option[String] = nameDelegate(t) map taskName
    def nameDelegate(t: Task[_]): Option[Task[_]] =
      Option(anonOwners.get(t)) orElse Option(calledBy.get(t))
    definedName(t) orElse inferredName(t) getOrElse TaskName.anonymousName(t)
  }
}

private[sbt] object TaskProgress {
  final val DeleteLine1 = "\u001B[2K"
  final val CursorUp5 = cursorUp(5)
  def cursorUp(n: Int): String = s"\u001B[${n}A"
  def cursorDown(n: Int): String = s"\u001B[${n}B"
  final val CursorDown1 = cursorDown(1)
}
