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
import sbt.SuiteResult
import sbt.Tests
import sbt.TestsFailedException
import sbt.protocol.testing.TestResult
import sbt.util.Logger

object TestRecapTest extends verify.BasicTestSuite:

  private def output(result: TestResult, suites: (String, SuiteResult)*): Tests.Output =
    Tests.Output(result, suites.toMap, Iterable.empty)

  private def suite(result: TestResult): SuiteResult =
    new SuiteResult(result, 0, 1, 0, 0, 0, 0, 0)

  private def failure(
      taskName: String,
      result: TestResult,
      suiteName: String
  ): TestsFailedException =
    new TestsFailedException(
      taskName,
      Some(output(result, suiteName -> suite(result)))
    )

  /** Build an Incomplete tree carrying the given TestsFailedExceptions as direct causes. */
  private def incompleteOf(exceptions: TestsFailedException*): Incomplete =
    new Incomplete(
      node = None,
      causes = exceptions.map(e => new Incomplete(node = None, directCause = Some(e)))
    )

  private class Capture extends Logger:
    val lines: scala.collection.mutable.ArrayBuffer[(String, String)] =
      scala.collection.mutable.ArrayBuffer.empty
    override def trace(t: => Throwable): Unit = ()
    override def success(msg: => String): Unit = ()
    override def log(level: sbt.util.Level.Value, msg: => String): Unit =
      lines += level.toString -> msg

  test("collect picks up TestsFailedException payloads from the Incomplete tree") {
    val i = incompleteOf(
      failure("a / Test / test", TestResult.Failed, "AFailing"),
      failure("c / Test / test", TestResult.Error, "CErroring"),
    )
    val collected = TestRecap.collect(i)
    assert(collected.map(_.taskName).sorted == Seq("a / Test / test", "c / Test / test"))
    val resultsBy = collected.flatMap(f => f.testOutput.map(o => f.taskName -> o.overall)).toMap
    assert(resultsBy("a / Test / test") == TestResult.Failed)
    assert(resultsBy("c / Test / test") == TestResult.Error)
  }

  test("collect retains TestsFailedException without payload as a stub entry") {
    val noDetail = new TestsFailedException // back-compat no-arg
    val i = new Incomplete(
      node = None,
      causes = Seq(
        new Incomplete(node = None, directCause = Some(noDetail)),
        new Incomplete(
          node = None,
          directCause = Some(failure("ok / Test / test", TestResult.Failed, "OkFail"))
        ),
      )
    )
    val collected = TestRecap.collect(i)
    assert(collected.size == 2, s"expected 2 entries, got $collected")
    val stub = collected.find(_.testOutput.isEmpty)
    assert(stub.isDefined, "no-detail failure should still produce a Failure entry")
    assert(stub.get.taskName == "")
  }

  test("collect skips exceptions that aren't TestsFailedException") {
    val i = new Incomplete(
      node = None,
      causes = Seq(
        new Incomplete(node = None, directCause = Some(new RuntimeException("nope"))),
        new Incomplete(
          node = None,
          directCause = Some(failure("ok / Test / test", TestResult.Failed, "OkFail"))
        ),
      )
    )
    assert(TestRecap.collect(i).map(_.taskName) == Seq("ok / Test / test"))
  }

  test("render emits header, per-task counts, and indented suite names") {
    val failures = Seq(
      TestRecap.Failure(
        "a / Test / test",
        Some(output(TestResult.Failed, "AFailing" -> suite(TestResult.Failed)))
      ),
      TestRecap.Failure(
        "c / Test / test",
        Some(output(TestResult.Error, "CErroring" -> suite(TestResult.Error)))
      ),
    )
    val lines = TestRecap.render(failures)
    assert(lines.headOption.contains("Test failures recap (2 test tasks failed):"))
    assert(lines.exists(_.contains("a / Test / test:")))
    assert(lines.exists(_.contains("c / Test / test:")))
    assert(lines.exists(_.contains("AFailing")))
    assert(lines.exists(_.contains("CErroring")))
    assert(lines.contains("    Failed tests:"))
    assert(lines.contains("    Error during tests:"))
    // ASCII-only output for terminal/CI compatibility.
    lines.foreach: l =>
      assert(l.forall(ch => ch < 128), s"non-ASCII characters in recap line: $l")
  }

  test("render emits singular header when exactly one task failed") {
    val one = Seq(
      TestRecap.Failure(
        "a / Test / test",
        Some(output(TestResult.Failed, "AFailing" -> suite(TestResult.Failed)))
      )
    )
    assert(TestRecap.render(one).head == "Test failures recap (1 test task failed):")
  }

  test("render shows '(no details)' for failures without a Tests.Output payload") {
    val failures = Seq(TestRecap.Failure("a / Test / test", testOutput = None))
    val lines = TestRecap.render(failures)
    assert(
      lines.exists(_.contains("a / Test / test: (no details)")),
      s"expected '(no details)' entry, got $lines"
    )
  }

  test("render shows '<unknown>' when a failure carries no task name") {
    val failures = Seq(TestRecap.Failure(taskName = "", testOutput = None))
    val lines = TestRecap.render(failures)
    assert(
      lines.exists(_.contains("<unknown>: (no details)")),
      s"expected '<unknown>' placeholder, got $lines"
    )
  }

  test("render is empty when there are no failures") {
    assert(TestRecap.render(Seq.empty).isEmpty)
  }

  test("formatTo emits one error-level log line per rendered line") {
    val failures = Seq(
      TestRecap.Failure(
        "a / Test / test",
        Some(output(TestResult.Failed, "AFailing" -> suite(TestResult.Failed)))
      )
    )
    val log = new Capture
    TestRecap.formatTo(log, failures)
    val rendered = TestRecap.render(failures)
    assert(
      log.lines.size == rendered.size,
      s"expected ${rendered.size} log calls, got ${log.lines.size}"
    )
    assert(log.lines.forall(_._1 == "error"), s"all lines should be error level: ${log.lines}")
  }

  test("formatTo is a no-op when there are no failures") {
    val log = new Capture
    TestRecap.formatTo(log, Seq.empty)
    assert(log.lines.isEmpty)
  }

  test("recapKey label is stable for tooling identity") {
    // AttributeKey converts hyphenated names to camelCase via Util.hyphenToCamel.
    assert(TestRecap.recapKey.label == "testRecap", s"actual label: ${TestRecap.recapKey.label}")
  }

end TestRecapTest
