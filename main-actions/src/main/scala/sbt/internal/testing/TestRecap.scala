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

import sbt.Incomplete
import sbt.Tests
import sbt.TestResultLogger
import sbt.TestsFailedException
import sbt.protocol.testing.TestResult
import sbt.internal.util.AttributeKey
import sbt.util.Logger

/**
 * Stateless formatter that surfaces every failed test task at the end of an
 * aggregated run (see sbt/sbt#2998). The data is read directly off the
 * `Incomplete` tree returned by `Aggregation.runTasks`: each subproject's
 * `testFull` / `inputTests0` catches the `TestsFailedException` thrown by
 * `TestResultLogger.Defaults.Main.run` and re-throws with `(taskName,
 * Some(Tests.Output))` attached, and we collect those instances from the
 * tree.
 *
 * The collected `Vector[Failure]` is also stashed on `State.attributes`
 * under `recapKey` so in-JVM tools (IDE plugins, BSP servers, scripted
 * tests that stay inside one sbt invocation via `Command.process`) can
 * inspect the most recent recap without parsing log output. Scripted tests
 * crossing a `->` boundary cannot read this because the inner sbt's IPC
 * server is torn down on failure and a fresh JVM is spawned for the next
 * statement.
 *
 * Lifecycle is monotonic-latest-failure: `Aggregation.runTasks` writes
 * `recapKey` whenever a run produces at least one `TestsFailedException`,
 * and never removes it. A successful test run after a failure leaves the
 * stale attribute in place; the next failure will overwrite it. We do not
 * attempt to recognize "this is a test invocation" at the aggregation
 * boundary to avoid a hardcoded list of test-task labels (or a
 * Tags-detection design exercise).
 */
private[sbt] object TestRecap:

  /**
   * A single failed test task contributing to the recap.
   *
   * `testOutput` is sanitized by [[collect]]: its `SuiteResult.throwables` are dropped so the
   * recap cannot pin the test class loader. See [[collect]] for why.
   */
  final case class Failure(taskName: String, testOutput: Option[Tests.Output])

  /**
   * State attribute holding the collected failures from the most recent
   * aggregated run that produced at least one `TestsFailedException`.
   * Monotonic-latest-failure semantics: never cleared on success, only
   * overwritten by the next failure.
   *
   * Note for consumers: the `SuiteResult.throwables` reachable from these
   * failures are always empty -- [[collect]] strips them so the recap cannot
   * pin the test class loader. An empty `throwables` here therefore means
   * "not retained", NOT "no exception was thrown". The real throwables live on
   * the `TestsFailedException` in the `Incomplete` tree, which is where error
   * reporting reads them from.
   */
  val recapKey: AttributeKey[Vector[Failure]] = AttributeKey[Vector[Failure]](
    "testRecap",
    "Failures collected from the most recent aggregated test run"
  )

  /**
   * Walk the `Incomplete` tree and return one `Failure` per
   * `TestsFailedException`. Exceptions without a payload (e.g., the
   * back-compat no-arg constructor escaping a path that didn't get wrapped
   * at the task boundary) still contribute a stub entry so the recap lists
   * at least the task name when one is available.
   *
   * Identity-deduplicated via `Incomplete.allExceptions` (which uses an
   * `IDSet[Throwable]` internally), so a single failing task shared across
   * multiple Incomplete paths in a DAG is counted once.
   *
   * The retained `Tests.Output` is stripped of `SuiteResult.throwables`: the
   * recap only renders names and counts, but a test-thrown Throwable's
   * backtrace pins the `Class` objects of every frame, and a `Class` strongly
   * references its defining class loader. Since this data is stashed on
   * `State.attributes` where it outlives the command, retaining the throwables
   * would keep the test class loader -- and its open jar handles -- alive for
   * the rest of the session. On Windows those handles make the cached jars
   * undeletable (e.g. by `clearCaches`).
   */
  def collect(i: Incomplete): Vector[Failure] =
    Incomplete
      .allExceptions(i)
      .iterator
      .flatMap {
        case e: TestsFailedException =>
          Some(Failure(e.taskName, e.testOutput.map(dropThrowables)))
        case _ => None
      }
      .toVector

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

  /**
   * The rendered recap as a sequence of `\n`-free lines. Failures are
   * sorted by `taskName` (lexicographically; empty task names last) for
   * stable, diff-friendly output across runs.
   */
  def render(failures: Vector[Failure]): Vector[String] =
    if failures.isEmpty then Vector.empty
    else
      val sorted = failures.sortBy(f => (f.taskName.isEmpty, f.taskName))
      val n = sorted.size
      val plural = if n == 1 then "" else "s"
      val lines = Vector.newBuilder[String]
      lines += s"Test failures recap ($n test task$plural failed):"
      sorted.foreach { f =>
        val displayName = if f.taskName.isEmpty then "<unknown>" else f.taskName
        f.testOutput match
          case None =>
            lines += s"  $displayName: (no details)"
          case Some(out) =>
            lines += s"  $displayName: ${TestResultLogger.Defaults.countsString(out)}"
            val failed = collectByResult(out, TestResult.Failed)
            val errored = collectByResult(out, TestResult.Error)
            if failed.nonEmpty then
              lines += "    Failed tests:"
              failed.foreach(name => lines += s"      $name")
            if errored.nonEmpty then
              lines += "    Error during tests:"
              errored.foreach(name => lines += s"      $name")
      }
      lines.result()

  /** Render `failures` and emit one error-level log line per rendered line. */
  def formatTo(log: Logger, failures: Vector[Failure]): Unit =
    render(failures).foreach(line => log.error(line))

  private def collectByResult(o: Tests.Output, target: TestResult): Vector[String] =
    // Mirrors `TestResultLogger.Defaults.printFailures` so the per-task
    // "Failed tests:" block and the cross-project recap render the same
    // suite name. Whether `NameTransformer.decode` should be applied to
    // suite FQNs at all is debatable, but changing both sites belongs in
    // a separate cleanup.
    o.events.iterator
      .collect {
        case (name, suite) if suite.result == target =>
          scala.reflect.NameTransformer.decode(name)
      }
      .toVector
      .sorted

end TestRecap
