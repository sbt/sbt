/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import sbt.TestResultLogger.Defaults.Summary
import sbt.internal.util.Terminal
import sbt.protocol.testing.TestResult
import sbt.util.Logger

import scala.Console.*
import scala.util.Using

object TestResultLoggerSummaryTest extends verify.BasicTestSuite:

  private def output(suites: (String, SuiteResult)*): Tests.Output =
    Tests.Output(TestResult.Passed, suites.toMap, Iterable.empty)

  private def suite(passed: Int, failed: Int = 0, errors: Int = 0): SuiteResult =
    val result =
      if errors > 0 then TestResult.Error
      else if failed > 0 then TestResult.Failed
      else TestResult.Passed
    new SuiteResult(result, passed, failed, errors, 0, 0, 0, 0)

  private def entry(
      taskName: String,
      out: Tests.Output,
      cached: Vector[String] = Vector.empty
  ): (Tests.Output, String, Vector[String]) = (out, taskName, cached)

  private def render(mode: TestSummary, entries: Vector[(Tests.Output, String, Vector[String])]) =
    Summary.render(mode, entries, isColorEnabled = false)

  private class Capture extends Logger with AutoCloseable:
    val lines: scala.collection.mutable.ArrayBuffer[(String, String)] =
      scala.collection.mutable.ArrayBuffer.empty
    override def trace(t: => Throwable): Unit = ()
    override def success(msg: => String): Unit = ()
    override def log(level: sbt.util.Level.Value, msg: => String): Unit =
      lines += level.toString -> msg
    def close(): Unit = ()
  end Capture

  private def withLog[A1](f: Capture => A1): A1 =
    Using.resource(new Capture): log =>
      f(log)

  test("render is empty when nothing ran and nothing was cached") {
    val entries = Vector(entry("a / Test / test", output()))
    assert(render(TestSummary.default, entries).isEmpty)
    assert(render(TestSummary.Success, entries).isEmpty)
  }

  test("Default renders one aggregate line across executed and cached tests") {
    val entries = Vector(
      entry("a / Test / test", output("A" -> suite(passed = 2))),
      entry("b / Test / test", output("B" -> suite(passed = 1)), Vector("B2")),
    )
    assert(
      render(TestSummary.default, entries) ==
        Vector("passed: total 3, failed 0, errors 0, passed 3, cached 1")
    )
  }

  test("render leads with the aggregate status") {
    val entries = Vector(entry("a / Test / test", output("A" -> suite(passed = 1, failed = 1))))
    assert(
      render(TestSummary.default, entries).last ==
        "failed: total 1, failed 1, errors 0, passed 0, cached 0"
    )
    val errored = Vector(entry("a / Test / test", output("A" -> suite(passed = 0, errors = 1))))
    assert(
      render(TestSummary.default, errored).last ==
        "error: total 1, failed 0, errors 1, passed 0, cached 0"
    )
  }

  test("Default lists only the non-passing task among a mix of pass/fail/cached") {
    val entries = Vector(
      entry(
        "a / Test / test",
        output("example.Failing" -> new SuiteResult(TestResult.Failed, 0, 1, 0, 0, 0, 0, 0)),
      ),
      entry("b / Test / test", output("example.Passing" -> suite(passed = 1))),
      entry("c / Test / test", output(), Vector("example.Cached")),
    )
    assert(
      render(TestSummary.default, entries) ==
        Vector(
          "Test summary (1 test task failed):",
          "  a / Test / test",
          "        example.Failing     FAIL",
          "",
          "failed: total 3, failed 1, errors 0, passed 2, cached 1",
        )
    )
  }

  test("Success renders the per-suite detail with cached markers") {
    val entries = Vector(
      entry("core2 / Test / testQuick", output("example.ExampleSuite2" -> suite(passed = 1))),
      entry(
        "core1 / Test / testQuick",
        output("example.ExampleSuite1" -> suite(passed = 1)),
        Vector("example.ExampleTest1B"),
      ),
      entry("core3 / Test / testQuick", output(), Vector("example.ExampleSuite3")),
    )
    assert(
      render(TestSummary.Success, entries) ==
        Vector(
          "Test summary (3 test tasks succeeded):",
          "  core1 / Test / testQuick",
          "        example.ExampleSuite1     PASS",
          "        example.ExampleTest1B     (cached) PASS",
          "  core2 / Test / testQuick",
          "        example.ExampleSuite2     PASS",
          "  core3 / Test / testQuick",
          "        example.ExampleSuite3     (cached) PASS",
          "",
          "passed: total 4, failed 0, errors 0, passed 4, cached 2",
        )
    )
  }

  test("Success header counts only the failing task even though the body lists every task") {
    val entries = Vector(
      entry(
        "a / Test / test",
        output("example.Failing" -> new SuiteResult(TestResult.Failed, 0, 1, 0, 0, 0, 0, 0)),
      ),
      entry("b / Test / test", output("example.Passing" -> suite(passed = 1))),
    )
    assert(
      render(TestSummary.Success, entries) ==
        Vector(
          "Test summary (1 test task failed):",
          "  a / Test / test",
          "        example.Failing     FAIL",
          "  b / Test / test",
          "        example.Passing     PASS",
          "",
          "failed: total 2, failed 1, errors 0, passed 1, cached 0",
        )
    )
  }

  test("Failure lists only failing suites, collapsing to the aggregate line when all pass") {
    val failedSuite = new SuiteResult(TestResult.Failed, 0, 1, 0, 0, 0, 0, 0)
    val entries = Vector(
      entry(
        "a / Test / test",
        output("example.Passing" -> suite(passed = 1), "example.Failing" -> failedSuite),
        Vector("example.Cached"),
      )
    )
    assert(
      render(TestSummary.Failure, entries) ==
        Vector(
          "Test summary (1 test task failed):",
          "  a / Test / test",
          "        example.Failing     FAIL",
          "",
          "failed: total 3, failed 1, errors 0, passed 2, cached 1",
        )
    )
    val allPassing =
      Vector(entry("a / Test / test", output("example.Passing" -> suite(passed = 1))))
    assert(
      render(TestSummary.Failure, allPassing) ==
        Vector("passed: total 1, failed 0, errors 0, passed 1, cached 0")
    )
  }

  test("isColorEnabled wraps only the status word, not a (cached) prefix") {
    val entries = Vector(
      entry(
        "a / Test / test",
        output(
          "example.Passing" -> suite(passed = 1),
          "example.Failing" -> new SuiteResult(TestResult.Failed, 0, 1, 0, 0, 0, 0, 0),
        ),
        Vector("example.Cached"),
      )
    )
    val lines = Summary.render(TestSummary.Success, entries, isColorEnabled = true)
    assert(lines.exists(_.endsWith(s"${GREEN}PASS$RESET")))
    assert(lines.exists(_.endsWith(s"${RED}FAIL$RESET")))
    assert(lines.exists(l => l.contains("(cached) ") && l.endsWith(s"${GREEN}PASS$RESET")))
    assert(!lines.exists(_.contains(s"$GREEN(cached)")))
  }

  test("None suppresses all output, even on failure") {
    val entries = Vector(
      entry(
        "a / Test / test",
        output("example.Failing" -> new SuiteResult(TestResult.Failed, 0, 1, 0, 0, 0, 0, 0)),
      )
    )
    assert(render(TestSummary.none, entries).isEmpty)
  }

  test("summary logs at info level when everything passes") {
    withLog: log =>
      val entries = Vector(entry("a / Test / test", output("example.Passing" -> suite(passed = 1))))
      Summary(TestSummary.Success).summary(log, entries)
      val expected = Summary
        .render(TestSummary.Success, entries, Terminal.get.isColorEnabled)
        .map(line => if line.isEmpty then " " else line)
      assert(log.lines.map(_._2).toVector == expected)
      assert(log.lines.forall(_._1 == "info"))
  }

  test("summary logs at error level when entries contain a failure") {
    withLog: log =>
      val entries = Vector(
        entry(
          "a / Test / test",
          output("example.Failing" -> new SuiteResult(TestResult.Failed, 0, 1, 0, 0, 0, 0, 0))
        )
      )
      Summary(TestSummary.default).summary(log, entries)
      val expected = Summary
        .render(TestSummary.default, entries, Terminal.get.isColorEnabled)
        .map(line => if line.isEmpty then " " else line)
      assert(log.lines.map(_._2).toVector == expected)
      assert(log.lines.nonEmpty)
      assert(log.lines.forall(_._1 == "error"))
  }

  test("Summary.run delegates to the Default per-task logger"):
    withLog: log =>
      Summary().run(log, output("A" -> suite(passed = 1)), "a / Test / test")
      assert(log.lines.exists(_._2 == "passed: total 1, failed 0, errors 0, passed 1, cached 0"))

  test("printStandard's 4-arg run folds the cached count into total and passed"):
    withLog: log =>
      TestResultLogger.Default.run(
        log,
        output("A" -> suite(passed = 1)),
        "a / Test / test",
        Vector("B", "C"),
      )
      assert(log.lines.exists(_._2 == "passed: total 3, failed 0, errors 0, passed 3, cached 2"))

  test("printStandard's 3-arg run always shows the cached count, even at zero"):
    withLog: log =>
      TestResultLogger.Default.run(log, output("A" -> suite(passed = 1)), "a / Test / test")
      assert(log.lines.exists(_._2 == "passed: total 1, failed 0, errors 0, passed 1, cached 0"))

  test("choose (via SilentWhenNoTests) forwards the cached count to the chosen branch"):
    withLog: log =>
      TestResultLogger.SilentWhenNoTests.run(
        log,
        output("A" -> suite(passed = 1)),
        "a / Test / test",
        Vector("B", "C"),
      )
      assert(log.lines.exists(_._2 == "passed: total 3, failed 0, errors 0, passed 3, cached 2"))

end TestResultLoggerSummaryTest
