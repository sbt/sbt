/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.io.File
import java.nio.file.Files
import sbt.internal.util.{ RMap, ConsoleOut }
import sbt.io.IO
import sbt.io.syntax._
import sjsonnew.shaded.scalajson.ast.unsafe.JString
import sjsonnew.support.scalajson.unsafe.CompactPrinter

/**
 * Measure the time elapsed for running tasks, and write the result out
 * as Chrome Trace Event Format.
 * This class is activated by adding -Dsbt.traces=true to the JVM options.
 */
private[sbt] final class TaskTraceEvent
    extends AbstractTaskExecuteProgress
    with ExecuteProgress[Task] {
  import AbstractTaskExecuteProgress.Timer
  private[this] var start = 0L
  private[this] val console = ConsoleOut.systemOut

  override def initial(): Unit = ()
  override def afterReady(task: Task[_]): Unit = ()
  override def afterCompleted[T](task: Task[T], result: Result[T]): Unit = ()
  override def afterAllCompleted(results: RMap[Task, Result]): Unit = ()
  override def stop(): Unit = ()

  start = System.nanoTime
  ShutdownHooks.add(() => report())

  private[this] def report() = {
    if (anyTimings) {
      writeTraceEvent()
    }
  }

  private[this] def writeTraceEvent(): Unit = {
    // import java.time.{ ZonedDateTime, ZoneOffset }
    // import java.time.format.DateTimeFormatter
    // val fileName = "build-" + ZonedDateTime
    //   .now(ZoneOffset.UTC)
    //   .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmmss")) + ".trace"
    val fileName = "build.trace"
    val tracesDirectory = (new File("target").getAbsoluteFile) / "traces"
    if (!tracesDirectory.exists) IO.createDirectory(tracesDirectory)
    else ()
    val outFile = tracesDirectory / fileName
    val trace = Files.newBufferedWriter(outFile.toPath)
    try {
      trace.append("""{"traceEvents": [""")
      def durationEvent(name: String, cat: String, t: Timer): String = {
        val sb = new java.lang.StringBuilder(name.length + 2)
        CompactPrinter.print(new JString(name), sb)
        s"""{"name": ${sb.toString}, "cat": "$cat", "ph": "X", "ts": ${(t.startMicros)}, "dur": ${(t.durationMicros)}, "pid": 0, "tid": ${t.threadId}}"""
      }
      val entryIterator = currentTimings
      while (entryIterator.hasNext) {
        val (key, value) = entryIterator.next()
        trace.append(durationEvent(taskName(key), "task", value))
        if (entryIterator.hasNext) trace.append(",")
      }
      trace.append("]}")
      ()
    } finally {
      trace.close()
      try console.println(s"wrote $outFile")
      catch { case _: java.nio.channels.ClosedChannelException => }
    }
  }
}
