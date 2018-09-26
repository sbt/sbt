/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.io.BufferedWriter
import java.nio.file.{ Files, Paths }

import sbt.internal.util.RMap
import java.util.concurrent.ConcurrentHashMap

import TaskName._
import sjsonnew.shaded.scalajson.ast.unsafe.JString
import sjsonnew.support.scalajson.unsafe.CompactPrinter

import scala.collection.mutable

/**
 * Measure the time elapsed for running tasks.
 * This class is activated by adding -Dsbt.task.timing=true to the JVM options.
 * Formatting options:
 * - -Dsbt.task.timings.on.shutdown=true|false
 * - -Dsbt.task.timings.unit=number
 * - -Dsbt.task.timings.threshold=number
 * @param reportOnShutdown    Should the report be given when exiting the JVM (true) or immediately (false)?
 */
private[sbt] final class TaskTimings(reportOnShutdown: Boolean) extends ExecuteProgress[Task] {
  private[this] val calledBy = new ConcurrentHashMap[Task[_], Task[_]]
  private[this] val anonOwners = new ConcurrentHashMap[Task[_], Task[_]]
  private[this] val timings = new ConcurrentHashMap[Task[_], Timer]
  private[this] var start = 0L
  private[this] val threshold = java.lang.Long.getLong("sbt.task.timings.threshold", 0L)
  private[this] val omitPaths = java.lang.Boolean.getBoolean("sbt.task.timings.omit.paths")
  private[this] val (unit, divider) = System.getProperty("sbt.task.timings.unit", "ms") match {
    case "ns" => ("ns", 0)
    case "us" => ("µs", 3)
    case "ms" => ("ms", 6)
    case "s"  => ("sec", 9)
    case x =>
      System.err.println(s"Unknown sbt.task.timings.unit: $x.\nUsing milliseconds.")
      ("ms", 6)
  }

  if (reportOnShutdown) {
    start = System.nanoTime
    Runtime.getRuntime.addShutdownHook(new Thread {
      override def run() = report()
    })
  }

  override def initial(): Unit = {
    if (!reportOnShutdown)
      start = System.nanoTime
  }
  override def afterRegistered(
      task: Task[_],
      allDeps: Iterable[Task[_]],
      pendingDeps: Iterable[Task[_]]
  ): Unit = {
    pendingDeps foreach { t =>
      if (transformNode(t).isEmpty) anonOwners.put(t, task)
    }
  }
  override def afterReady(task: Task[_]): Unit = ()
  override def beforeWork(task: Task[_]): Unit = { timings.put(task, new Timer); () }
  override def afterWork[T](task: Task[T], result: Either[Task[T], Result[T]]) = {
    timings.get(task).stop()
    result.left.foreach { t =>
      calledBy.put(t, task)
    }
  }
  override def afterCompleted[T](task: Task[T], result: Result[T]): Unit = ()
  override def afterAllCompleted(results: RMap[Task, Result]): Unit =
    if (!reportOnShutdown) {
      report()
    }
  private class Timer() {
    val startNanos = System.nanoTime()
    val threadId = Thread.currentThread().getId
    var endNanos = 0L
    def stop() = {
      endNanos = System.nanoTime()
    }
    def durationNanos = endNanos - startNanos
    def startMicros: Long = (startNanos.toDouble / 1000).toLong
    def durationMicros: Long = (durationNanos.toDouble / 1000).toLong
  }

  override def stop(): Unit = ()

  private val reFilePath = raw"\{[^}]+\}".r

  private[this] def report() = {
    val total = divide(System.nanoTime - start)
    println(s"Total time: $total $unit")
    import collection.JavaConverters._
    def sumTimes(in: Seq[(Task[_], Timer)]) = in.map(_._2.durationNanos).sum
    val timingsByName = timings.asScala.toSeq.groupBy { case (t, _) => mappedName(t) } mapValues (sumTimes)
    val times = timingsByName.toSeq
      .sortBy(_._2)
      .reverse
      .map {
        case (name, time) =>
          (if (omitPaths) reFilePath.replaceFirstIn(name, "") else name, divide(time))
      }
      .filter { _._2 > threshold }
    if (times.size > 0) {
      toChromeTrace()
      val maxTaskNameLength = times.map { _._1.length }.max
      val maxTime = times.map { _._2 }.max.toString.length
      times.foreach {
        case (taskName, time) =>
          println(s"  ${taskName.padTo(maxTaskNameLength, ' ')}: ${""
            .padTo(maxTime - time.toString.length, ' ')}$time $unit")
      }
    }
  }

  private def toChromeTrace(): Unit = {
    val path = Files.createTempFile("build-", ".trace")
    val trace = Files.newBufferedWriter(path)
    try {
      trace.append("""{"traceEvents": [""")
      def durationEvent(name: String, cat: String, t: Timer): String = {
        val sb = new java.lang.StringBuilder(name.length + 2)
        CompactPrinter.print(new JString(name), sb)
        // TODO use proper JSON library for all of this.
        s"""{"name": ${sb.toString}, "cat": "$cat", "ph": "X", "ts": ${(t.startMicros)}, "dur": ${(t.durationMicros)}, "pid": 0, "tid": ${t.threadId}}"""
      }
      val entryIterator = timings.entrySet().iterator()
      while (entryIterator.hasNext) {
        val entry = entryIterator.next()
        trace.append(durationEvent(mappedName(entry.getKey), "task", entry.getValue))
        if (entryIterator.hasNext) trace.append(",")
      }
      trace.append("]}")
    } finally {
      trace.close()
      println(path)

    }
  }

  private[this] def inferredName(t: Task[_]): Option[String] = nameDelegate(t) map mappedName
  private[this] def nameDelegate(t: Task[_]): Option[Task[_]] =
    Option(anonOwners.get(t)) orElse Option(calledBy.get(t))
  private[this] def mappedName(t: Task[_]): String =
    definedName(t) orElse inferredName(t) getOrElse anonymousName(t)
  private[this] def divide(time: Long) = (1L to divider.toLong).fold(time) { (a, b) =>
    a / 10L
  }
}
