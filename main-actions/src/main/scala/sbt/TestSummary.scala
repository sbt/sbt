/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.util.concurrent.ConcurrentLinkedQueue
import sbt.internal.util.AttributeKey

/**
 * Style of the test summary displayed after an aggregated test run:
 *   - `None`: prints nothing
 *   - `Failure` (the default): lists only non-passing suites, staying terse when everything passes
 *   - `Success`: always lists every suite, marking cache reuse with `(cached)`
 *
 * Rendering itself lives in `TestResultLogger.Defaults.Summary`, which
 * consumes the entries collected here.
 */
enum TestSummary:
  case None
  case Failure
  case Success

/**
 * Collector surfacing a test summary at the end of an aggregated run,
 * success or failure alike.
 */
object TestSummary:
  val none: TestSummary = TestSummary.None
  val failure: TestSummary = TestSummary.Failure
  val success: TestSummary = TestSummary.Success
  def default: TestSummary = TestSummary.failure

  /** `cached` names the test classes reused from the action cache (see `IncrementalTest.cachedTestNames`). */
  private[sbt] final case class Entry(
      taskName: String,
      testOutput: Tests.Output,
      cached: Vector[String],
      options: Vector[Tests.AdhocOption]
  )

  /**
   * State attribute holding the entries from the most recent aggregated run
   * that produced at least one test result. Monotonic-latest-run semantics:
   * never cleared, only overwritten by the next non-empty run; lets in-JVM
   * tools (IDE plugins, BSP servers, scripted tests staying inside one sbt
   * invocation via `Command.process`) inspect the last test results without
   * parsing log output. Scripted tests crossing a `->` boundary cannot read
   * this because the inner sbt's IPC server is torn down on failure and a
   * fresh JVM is spawned for the next statement.
   */
  private[sbt] val entriesKey: AttributeKey[Vector[Entry]] = AttributeKey[Vector[Entry]](
    "testSummaryEntries",
    "Entries collected from the most recent aggregated test run"
  )

  private val entries = new ConcurrentLinkedQueue[Entry]

  /**
   * `output`'s `SuiteResult.throwables` are dropped before retention: a
   * test-thrown Throwable's backtrace pins the `Class` objects of every
   * frame, and a `Class` strongly references its defining class loader.
   * Since entries are stashed on `State.attributes` where they outlive the
   * command, retaining the throwables would keep the test class loader --
   * and its open jar handles -- alive for the rest of the session. On
   * Windows those handles make the cached jars undeletable (e.g. by
   * `clearCaches`).
   */
  private[sbt] def append(
      taskName: String,
      output: Tests.Output,
      cached: Vector[String],
      adhocOptions: Vector[Tests.AdhocOption]
  ): Unit =
    entries.add(Entry(taskName, dropThrowables(output), cached, adhocOptions))
    ()

  private def dropThrowables(o: Tests.Output): Tests.Output =
    o.copy(events = o.events.view.mapValues(dropThrowables).toMap)

  private def dropThrowables(s: SuiteResult): SuiteResult =
    if s.throwables.isEmpty then s
    else
      new SuiteResult(
        s.result,
        s.passedCount,
        s.failureCount,
        s.errorCount,
        s.skippedCount,
        s.ignoredCount,
        s.canceledCount,
        s.pendingCount,
      )

  private[sbt] def clear(): Unit = entries.clear()

  private[sbt] def drain(): Vector[Entry] =
    val b = Vector.newBuilder[Entry]
    var e = entries.poll()
    while e != null do
      b += e
      e = entries.poll()
    b.result()

end TestSummary
