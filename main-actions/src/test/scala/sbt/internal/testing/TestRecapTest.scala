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

import sbt.SuiteResult
import sbt.Tests
import sbt.protocol.testing.TestResult
import sbt.util.Logger

object TestRecapTest extends verify.BasicTestSuite:

  private def output(result: TestResult, suites: (String, SuiteResult)*): Tests.Output =
    Tests.Output(result, suites.toMap, Iterable.empty)

  private def suite(result: TestResult): SuiteResult =
    new SuiteResult(result, 0, 1, 0, 0, 0, 0, 0)

  private class Capture extends Logger:
    val lines: scala.collection.mutable.ArrayBuffer[(String, String)] =
      scala.collection.mutable.ArrayBuffer.empty
    override def trace(t: => Throwable): Unit = ()
    override def success(msg: => String): Unit = ()
    override def log(level: sbt.util.Level.Value, msg: => String): Unit =
      lines += level.toString -> msg

  // Restore TestRecap to a clean state between tests; the singleton outlives
  // any single test method.
  private def withClean(body: => Unit): Unit =
    TestRecap.clear()
    TestRecap.clear() // double-clear to drop both `previousRecords` and `records`
    body

  test("recordRun appends only failures and errors") {
    withClean:
      TestRecap.recordRun("p / Test / test", output(TestResult.Passed))
      TestRecap.recordRun("q / Test / test", output(TestResult.Empty))
      TestRecap.recordRun(
        "r / Test / test",
        output(TestResult.Failed, "RFailing" -> suite(TestResult.Failed))
      )
      TestRecap.recordRun(
        "s / Test / test",
        output(TestResult.Error, "SErroring" -> suite(TestResult.Error))
      )
      val snap = TestRecap.snapshot
      assert(snap.size == 2, s"expected 2 records, got ${snap.map(_.taskName)}")
      assert(snap.exists(_.taskName == "r / Test / test"))
      assert(snap.exists(_.taskName == "s / Test / test"))
  }

  test("clear rolls records into previousSnapshot when a test ran") {
    withClean:
      TestRecap.recordRun(
        "x / Test / test",
        output(TestResult.Failed, "XFail" -> suite(TestResult.Failed))
      )
      TestRecap.clear()
      assert(TestRecap.snapshot.isEmpty)
      val prev = TestRecap.previousSnapshot
      assert(prev.size == 1, s"expected 1 previous record, got $prev")
      assert(prev.head.taskName == "x / Test / test")
  }

  test("clear without a test run leaves previousSnapshot untouched") {
    withClean:
      // Seed previousSnapshot with a failing run.
      TestRecap.recordRun(
        "p / Test / test",
        output(TestResult.Failed, "PFail" -> suite(TestResult.Failed))
      )
      TestRecap.clear()
      assert(TestRecap.previousSnapshot.size == 1)
      // A subsequent housekeeping `clear` (no test ran) must not erase it.
      TestRecap.clear()
      assert(
        TestRecap.previousSnapshot.size == 1,
        "intermediate non-test clear should not erase prior recap"
      )
  }

  test("clear after a passing-only test cycle empties previousSnapshot") {
    withClean:
      TestRecap.recordRun(
        "p / Test / test",
        output(TestResult.Failed, "PFail" -> suite(TestResult.Failed))
      )
      TestRecap.clear()
      assert(TestRecap.previousSnapshot.size == 1)
      // Next cycle: only a passing test ran. clear should reset previous to [].
      TestRecap.recordRun("p / Test / test", output(TestResult.Passed))
      TestRecap.clear()
      assert(
        TestRecap.previousSnapshot.isEmpty,
        "passing-only cycle should clear previous failures"
      )
  }

  test("formatTo emits a header, per-task counts, and indented suite names") {
    withClean:
      TestRecap.recordRun(
        "a / Test / test",
        output(TestResult.Failed, "AFailing" -> suite(TestResult.Failed))
      )
      TestRecap.recordRun(
        "c / Test / test",
        output(TestResult.Error, "CErroring" -> suite(TestResult.Error))
      )
      val log = new Capture
      TestRecap.formatTo(log)
      val joined = log.lines.map(_._2).mkString("\n")
      assert(joined.contains("Test failures recap (2 test tasks failed):"))
      assert(joined.contains("a / Test / test"))
      assert(joined.contains("c / Test / test"))
      assert(joined.contains("AFailing"))
      assert(joined.contains("CErroring"))
      assert(log.lines.forall(_._1 == "error"), s"all lines should be error level: ${log.lines}")
  }

  test("formatTo is a no-op when no failures recorded") {
    withClean:
      TestRecap.recordRun("p / Test / test", output(TestResult.Passed))
      val log = new Capture
      TestRecap.formatTo(log)
      assert(log.lines.isEmpty, s"expected no output, got ${log.lines}")
  }

  test("concurrent recordRun calls are thread-safe") {
    withClean:
      val threads = (0 until 16).map: i =>
        new Thread(() =>
          TestRecap.recordRun(
            s"p$i / Test / test",
            output(TestResult.Failed, s"P$i" -> suite(TestResult.Failed))
          )
        )
      threads.foreach(_.start())
      threads.foreach(_.join())
      assert(
        TestRecap.snapshot.size == 16,
        s"expected 16 records, got ${TestRecap.snapshot.size}"
      )
  }
end TestRecapTest
