/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package testing

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import scala.jdk.CollectionConverters.*

import sbt.Tests
import sbt.protocol.testing.TestResult
import sbt.util.Logger

/**
 * Thread-safe accumulator of per-task test failures collected during one
 * aggregated run. `Aggregation.runTasks` calls `clear` at the start of each
 * top-level invocation; the default `TestResultLogger` calls `recordRun`
 * once per subproject's test task; `formatTo` renders the snapshot to the
 * logger so users can see every failed task at the end of the run instead
 * of having to scroll back through thousands of log lines (sbt/sbt#2998).
 */
private[sbt] object TestRecap:
  final case class Record(taskName: String, output: Tests.Output)

  private val records = ConcurrentLinkedQueue[Record]()
  private val testRan = AtomicBoolean(false)
  @volatile private var previousRecords: Vector[Record] = Vector.empty

  /**
   * Called once per subproject's test task completion. Marks that a test
   * task ran in this aggregation cycle and, if the result was a failure,
   * records it for the recap.
   */
  def recordRun(taskName: String, output: Tests.Output): Unit =
    testRan.set(true)
    output.overall match
      case TestResult.Failed | TestResult.Error =>
        val _ = records.add(Record(taskName, output))
      case _ => ()

  /**
   * Called at the start of every `Aggregation.runTasks`. If a test ran in
   * the previous cycle, the current records are rolled into
   * `previousSnapshot` (so callers can inspect the most recent test run's
   * failures). Otherwise `previousSnapshot` is left untouched, so a
   * recovery / housekeeping `runTasks` doesn't clobber the snapshot.
   */
  def clear(): Unit =
    if testRan.getAndSet(false) then
      previousRecords = records.iterator.asScala.toVector
    records.clear()

  /** Failures recorded in the current (still-running) cycle. */
  def snapshot: Seq[Record] = records.iterator.asScala.toVector

  /** Failures recorded in the most recent test cycle. */
  def previousSnapshot: Seq[Record] = previousRecords

  /**
   * Emit the current snapshot to `log` as an error-level block. No-op if
   * no failures were recorded in this cycle.
   */
  def formatTo(log: Logger): Unit =
    val failures = snapshot.filter: r =>
      r.output.overall match
        case TestResult.Failed | TestResult.Error => true
        case _                                    => false
    if failures.nonEmpty then
      val n = failures.size
      val plural = if n == 1 then "" else "s"
      log.error(s"Test failures recap ($n test task$plural failed):")
      failures.foreach: rec =>
        log.error(s"  ${rec.taskName} — ${countsLine(rec.output)}")
        val failedNames = collectByResult(rec.output, TestResult.Failed)
        val erroredNames = collectByResult(rec.output, TestResult.Error)
        if failedNames.nonEmpty then
          log.error("    Failed tests:")
          failedNames.foreach(name => log.error(s"      $name"))
        if erroredNames.nonEmpty then
          log.error("    Error during tests:")
          erroredNames.foreach(name => log.error(s"      $name"))

  private def countsLine(o: Tests.Output): String =
    val (skipped, errors, passed, failures, ignored, canceled, pending) =
      o.events.foldLeft((0, 0, 0, 0, 0, 0, 0)):
        case ((sk, er, pa, fa, ig, ca, pe), (_, ev)) =>
          (
            sk + ev.skippedCount,
            er + ev.errorCount,
            pa + ev.passedCount,
            fa + ev.failureCount,
            ig + ev.ignoredCount,
            ca + ev.canceledCount,
            pe + ev.pendingCount
          )
    val total = failures + errors + skipped + passed
    val base = s"Total $total, Failed $failures, Errors $errors, Passed $passed"
    val extras = Seq(
      "Skipped" -> skipped,
      "Ignored" -> ignored,
      "Canceled" -> canceled,
      "Pending" -> pending
    ).withFilter(_._2 > 0).map((label, count) => s", $label $count")
    base + extras.mkString

  private def collectByResult(o: Tests.Output, target: TestResult): Seq[String] =
    o.events.iterator.collect {
      case (name, suite) if suite.result == target =>
        scala.reflect.NameTransformer.decode(name)
    }.toVector.sorted

end TestRecap
