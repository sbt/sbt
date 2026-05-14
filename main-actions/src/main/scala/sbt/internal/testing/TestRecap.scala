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
 * `Incomplete` tree returned by `Aggregation.runTasks` -- each subproject's
 * `testFull` / `testQuick` throws `TestsFailedException` carrying the task
 * name and `Tests.Output`, and we collect those instances from the tree.
 *
 * The current snapshot is also stashed on `State.attributes` under
 * `recapKey` so tools / scripted tests can inspect the most recent recap
 * without parsing log output.
 */
private[sbt] object TestRecap:

  /** A single failed test task contributing to the recap. */
  final case class Failure(taskName: String, testOutput: Option[Tests.Output])

  /**
   * State attribute holding the collected failures from the most recent
   * aggregated test run. Replaced (or removed) only when a top-level run
   * actually included a test task, so unrelated tasks running between test
   * invocations don't clobber the recap.
   */
  val recapKey: AttributeKey[Vector[Failure]] = AttributeKey[Vector[Failure]](
    "test-recap",
    "Failures collected from the most recent aggregated test run",
    1000
  )

  /**
   * Walk the `Incomplete` tree and return one `Failure` per
   * `TestsFailedException`. Exceptions without a payload (e.g., the
   * back-compat no-arg constructor) still contribute an entry so the recap
   * lists at least the task name when one is available.
   */
  def collect(i: Incomplete): Seq[Failure] =
    Incomplete
      .allExceptions(i)
      .iterator
      .flatMap:
        case e: TestsFailedException => Some(Failure(e.taskName, e.testOutput))
        case _                       => None
      .toVector

  /** The rendered recap as a sequence of `\n`-free lines. */
  def render(failures: Seq[Failure]): Seq[String] =
    if failures.isEmpty then Vector.empty
    else
      val n = failures.size
      val plural = if n == 1 then "" else "s"
      val lines = Vector.newBuilder[String]
      lines += s"Test failures recap ($n test task$plural failed):"
      failures.foreach: f =>
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
      lines.result()

  /** Render `failures` and emit one error-level log line per rendered line. */
  def formatTo(log: Logger, failures: Seq[Failure]): Unit =
    render(failures).foreach(log.error(_))

  private def collectByResult(o: Tests.Output, target: TestResult): Seq[String] =
    o.events.iterator
      .collect {
        case (name, suite) if suite.result == target =>
          scala.reflect.NameTransformer.decode(name)
      }
      .toVector
      .sorted

end TestRecap
