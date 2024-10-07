/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import scala.jdk.CollectionConverters.*
import scala.collection.mutable
import scala.collection.immutable.VectorBuilder
import scala.concurrent.duration._

private[sbt] abstract class AbstractTaskExecuteProgress extends ExecuteProgress {
  import AbstractTaskExecuteProgress.Timer

  private val showScopedKey = Def.showShortKey(None)
  private val anonOwners = new ConcurrentHashMap[TaskId[_], TaskId[_]]
  private val calledBy = new ConcurrentHashMap[TaskId[_], TaskId[_]]
  private val timings = new ConcurrentHashMap[TaskId[_], Timer]
  private[sbt] def timingsByName: mutable.Map[String, AtomicLong] = {
    val result = new ConcurrentHashMap[String, AtomicLong]
    timings.forEach { (task, timing) =>
      val duration = timing.durationNanos
      result.putIfAbsent(taskName(task), new AtomicLong(duration)) match {
        case null =>
        case t    => t.getAndAdd(duration); ()
      }
    }
    result.asScala
  }
  private[sbt] def anyTimings = !timings.isEmpty
  def currentTimings: Iterator[(TaskId[_], Timer)] = timings.asScala.iterator

  private[internal] def exceededThreshold(task: TaskId[_], threshold: FiniteDuration): Boolean =
    timings.get(task) match {
      case null => false
      case t    => t.durationMicros > threshold.toMicros
    }
  private[internal] def timings(
      tasks: java.util.Set[TaskId[_]],
      thresholdMicros: Long
  ): Vector[(TaskId[_], Long)] = {
    val result = new VectorBuilder[(TaskId[_], Long)]
    val now = System.nanoTime
    tasks.forEach { t =>
      timings.get(t) match {
        case null =>
        case timing =>
          if (timing.isActive) {
            val elapsed = (now - timing.startNanos) / 1000
            if (elapsed > thresholdMicros) result += t -> elapsed
          }
      }
    }
    result.result()
  }
  def activeTasks(now: Long) = {
    val result = new VectorBuilder[(TaskId[_], FiniteDuration)]
    timings.forEach { (task, timing) =>
      if (timing.isActive) result += task -> (now - timing.startNanos).nanos
    }
    result.result
  }

  override def afterRegistered(
      task: TaskId[?],
      allDeps: Iterable[TaskId[?]],
      pendingDeps: Iterable[TaskId[?]]
  ): Unit = {
    // we need this to infer anonymous task names
    pendingDeps
      .filter {
        case t: Task[?] => TaskName.transformNode(t).isEmpty
        case _          => true
      }
      .foreach(anonOwners.put(_, task))
  }

  override def beforeWork(task: TaskId[?]): Unit = {
    timings.put(task, new Timer)
    ()
  }

  protected def clearTimings: Boolean = false
  override def afterWork[A](task: TaskId[A], result: Either[TaskId[A], Result[A]]): Unit = {
    if (clearTimings) timings.remove(task)
    else
      timings.get(task) match {
        case null =>
        case t    => t.stop()
      }

    // we need this to infer anonymous task names
    result.left.foreach { t =>
      calledBy.put(t, task)
    }
  }

  private val taskNameCache = new ConcurrentHashMap[TaskId[_], String]
  protected def taskName(t: TaskId[_]): String = taskNameCache.get(t) match {
    case null =>
      val name = taskName0(t)
      taskNameCache.putIfAbsent(t, name)
      name
    case name => name
  }
  private def taskName0(t: TaskId[_]): String = {
    def definedName(node: Task[_]): Option[String] =
      node.info.name.orElse(TaskName.transformNode(node).map(showScopedKey.show))
    def inferredName(t: Task[_]): Option[String] = nameDelegate(t) map taskName
    def nameDelegate(t: Task[_]): Option[TaskId[_]] =
      Option(anonOwners.get(t)).orElse(Option(calledBy.get(t)))
    t match
      case t: Task[?] => definedName(t).orElse(inferredName(t)).getOrElse(TaskName.anonymousName(t))
      case _          => TaskName.anonymousName(t)
  }
}

object AbstractTaskExecuteProgress {
  private[sbt] class Timer() {
    val startNanos: Long = System.nanoTime()
    val threadName: String = Thread.currentThread().getName
    var endNanos: Long = 0L
    def stop(): Unit = {
      endNanos = System.nanoTime()
    }
    def isActive = endNanos == 0L
    def durationNanos: Long = endNanos - startNanos
    def startMicros: Long = (startNanos.toDouble / 1000).toLong
    def durationMicros: Long = (durationNanos.toDouble / 1000).toLong
    def currentElapsedMicros: Long =
      ((System.nanoTime() - startNanos).toDouble / 1000).toLong
  }
}
