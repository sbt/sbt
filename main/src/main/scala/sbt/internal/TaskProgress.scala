/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import sbt.internal.util.{ RMap, ConsoleOut, ConsoleAppender, LogOption, JLine }
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.{ blocking, Future, ExecutionContext }
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicInteger }
import scala.collection.JavaConverters._
import scala.collection.concurrent.TrieMap
import TaskProgress._

/**
 * implements task progress display on the shell.
 */
private[sbt] final class TaskProgress(currentRef: ProjectRef) extends ExecuteProgress[Task] {
  private[this] val showScopedKey = Def.showRelativeKey2(currentRef)
  // private[this] var start = 0L
  private[this] val activeTasks = new ConcurrentHashMap[Task[_], Long]
  private[this] val timings = new ConcurrentHashMap[Task[_], Long]
  private[this] val calledBy = new ConcurrentHashMap[Task[_], Task[_]]
  private[this] val anonOwners = new ConcurrentHashMap[Task[_], Task[_]]
  private[this] val isReady = new AtomicBoolean(false)
  private[this] val lastTaskCount = new AtomicInteger(0)
  private[this] val isAllCompleted = new AtomicBoolean(false)
  private[this] val isStopped = new AtomicBoolean(false)

  override def initial(): Unit = {
    ConsoleAppender.setTerminalWidth(JLine.usingTerminal(_.getWidth))
  }

  override def afterRegistered(
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
  override def afterReady(task: Task[_]): Unit = {
    isReady.set(true)
  }

  override def beforeWork(task: Task[_]): Unit = {
    activeTasks.put(task, System.nanoTime)
    ()
  }

  override def afterWork[A](task: Task[A], result: Either[Task[A], Result[A]]): Unit = {
    val start = activeTasks.get(task)
    timings.put(task, System.nanoTime - start)
    activeTasks.remove(task)
    // we need this to infer anonymous task names
    result.left.foreach { t =>
      calledBy.put(t, task)
    }
  }

  override def afterCompleted[A](task: Task[A], result: Result[A]): Unit = ()

  override def stop(): Unit = {
    isStopped.set(true)
  }

  import ExecutionContext.Implicits._
  Future {
    while (!isReady.get && !isStopped.get) {
      blocking {
        Thread.sleep(500)
      }
    }
    while (!isAllCompleted.get && !isStopped.get) {
      blocking {
        report()
        Thread.sleep(500)
      }
    }
  }

  private[this] val console = ConsoleOut.systemOut
  override def afterAllCompleted(results: RMap[Task, Result]): Unit = {
    isAllCompleted.set(true)
    // completionReport()
  }
  private[this] val skipReportTasks =
    Set("run", "bgRun", "fgRun", "scala", "console", "consoleProject")
  private[this] def report(): Unit = console.lockObject.synchronized {
    val currentTasks = activeTasks.asScala.toList
    val ltc = lastTaskCount.get
    val currentTasksCount = currentTasks.size
    def report0(): Unit = {
      console.print(s"$CursorDown1")
      currentTasks foreach {
        case (task, start) =>
          val elapsed = (System.nanoTime - start) / 1000000000L
          console.println(s"$DeleteLine  | => ${taskName(task)} ${elapsed}s")
      }
      if (ltc > currentTasksCount) deleteConsoleLines(ltc - currentTasksCount)
      else ()
      console.print(cursorUp(math.max(currentTasksCount, ltc) + 1))
    }
    if (containsSkipTasks(currentTasks)) ()
    else report0()
    lastTaskCount.set(currentTasksCount)
  }

  // todo: use logger instead of console
  // private[this] def completionReport(): Unit = console.lockObject.synchronized {
  //   val completedTasks = timings.asScala.toList
  //   val notableTasks = completedTasks
  //     .filter({
  //       case (_, time: Long) => time >= 1000000000L * 10L
  //     })
  //     .sortBy({
  //       case (_, time: Long) => -time
  //     })
  //     .take(5)
  //   def report0(): Unit = {
  //     console.print(s"$CursorDown1")
  //     console.println(s"$DeleteLine  notable completed tasks:")
  //     notableTasks foreach {
  //       case (task, time) =>
  //         val elapsed = time / 1000000000L
  //         console.println(s"$DeleteLine  | => ${taskName(task)} ${elapsed}s")
  //     }
  //   }
  //   if (containsSkipTasks(notableTasks) || notableTasks.isEmpty) ()
  //   else report0()
  // }

  private[this] def containsSkipTasks(tasks: List[(Task[_], Long)]): Boolean =
    tasks
      .map({ case (t, _) => taskName(t) })
      .exists(n => skipReportTasks.exists(m => n.endsWith("/ " + m)))

  private[this] def deleteConsoleLines(n: Int): Unit = {
    (1 to n) foreach { _ =>
      console.println(s"$DeleteLine")
    }
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
  final val DeleteLine = "\u001B[2K"
  def cursorUp(n: Int): String = s"\u001B[${n}A"
  def cursorDown(n: Int): String = s"\u001B[${n}B"
  final val CursorDown1 = cursorDown(1)

  def isEnabled: Boolean =
    ConsoleAppender.formatEnabledInEnv && sys.props
      .get("sbt.supershell")
      .flatMap(
        str =>
          ConsoleAppender.parseLogOption(str) match {
            case LogOption.Always => Some(true)
            case LogOption.Never  => Some(false)
            case _                => None
        }
      )
      .getOrElse(true)
}
