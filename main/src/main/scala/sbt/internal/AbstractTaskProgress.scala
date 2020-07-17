/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.util.concurrent.ConcurrentHashMap
import scala.collection.concurrent.TrieMap
import scala.collection.JavaConverters._

private[sbt] abstract class AbstractTaskExecuteProgress extends ExecuteProgress[Task] {
  import AbstractTaskExecuteProgress.Timer

  private[this] val showScopedKey = Def.showShortKey(None)
  private[this] val anonOwners = new ConcurrentHashMap[Task[_], Task[_]]
  private[this] val calledBy = new ConcurrentHashMap[Task[_], Task[_]]
  private[this] val activeTasksMap = new ConcurrentHashMap[Task[_], Unit]
  protected val timings = new ConcurrentHashMap[Task[_], Timer]

  def activeTasks: Set[Task[_]] = activeTasksMap.keySet.asScala.toSet

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

  override def beforeWork(task: Task[_]): Unit = {
    timings.put(task, new Timer)
    activeTasksMap.put(task, ())
  }

  override def afterWork[A](task: Task[A], result: Either[Task[A], Result[A]]): Unit = {
    timings.get(task) match {
      case null =>
      case t    => t.stop()
    }
    activeTasksMap.remove(task)

    // we need this to infer anonymous task names
    result.left.foreach { t =>
      calledBy.put(t, task)
    }
  }

  protected def reset(): Unit = {
    activeTasksMap.clear()
    timings.clear()
  }

  private[this] val taskNameCache = TrieMap.empty[Task[_], String]
  protected def taskName(t: Task[_]): String =
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

object AbstractTaskExecuteProgress {
  private[sbt] class Timer() {
    val startNanos: Long = System.nanoTime()
    val threadId: Long = Thread.currentThread().getId
    var endNanos: Long = 0L
    def stop(): Unit = {
      endNanos = System.nanoTime()
    }
    def durationNanos: Long = endNanos - startNanos
    def startMicros: Long = (startNanos.toDouble / 1000).toLong
    def durationMicros: Long = (durationNanos.toDouble / 1000).toLong
    def currentElapsedMicros: Long =
      ((System.nanoTime() - startNanos).toDouble / 1000).toLong
  }
}
