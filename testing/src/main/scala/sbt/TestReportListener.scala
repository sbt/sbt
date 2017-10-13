/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import testing.{ Logger => TLogger, Event => TEvent, Status => TStatus }
import sbt.protocol.testing._

trait TestReportListener {

  /** called for each class or equivalent grouping */
  def startGroup(name: String): Unit

  /** called for each test method or equivalent */
  def testEvent(event: TestEvent): Unit

  /** called if there was an error during test */
  def endGroup(name: String, t: Throwable): Unit

  /** called if test completed */
  def endGroup(name: String, result: TestResult): Unit

  /** Used by the test framework for logging test results */
  def contentLogger(test: TestDefinition): Option[ContentLogger] = None

}

final class ContentLogger(val log: TLogger, val flush: () => Unit)

trait TestsListener extends TestReportListener {

  /** called once, at beginning. */
  def doInit(): Unit

  /** called once, at end of the test group. */
  def doComplete(finalResult: TestResult): Unit

}

/** Provides the overall `result` of a group of tests (a suite) and test counts for each result type. */
final class SuiteResult(
    val result: TestResult,
    val passedCount: Int,
    val failureCount: Int,
    val errorCount: Int,
    val skippedCount: Int,
    val ignoredCount: Int,
    val canceledCount: Int,
    val pendingCount: Int
) {
  def +(other: SuiteResult): SuiteResult = {
    val combinedTestResult =
      (result, other.result) match {
        case (TestResult.Passed, TestResult.Passed) => TestResult.Passed
        case (_, TestResult.Error)                  => TestResult.Error
        case (TestResult.Error, _)                  => TestResult.Error
        case _                                      => TestResult.Failed
      }
    new SuiteResult(
      combinedTestResult,
      passedCount + other.passedCount,
      failureCount + other.failureCount,
      errorCount + other.errorCount,
      skippedCount + other.skippedCount,
      ignoredCount + other.ignoredCount,
      canceledCount + other.canceledCount,
      pendingCount + other.pendingCount
    )
  }
}

object SuiteResult {

  /** Computes the overall result and counts for a suite with individual test results in `events`. */
  def apply(events: Seq[TEvent]): SuiteResult = {
    def count(status: TStatus) = events.count(_.status == status)
    new SuiteResult(
      TestEvent.overallResult(events),
      count(TStatus.Success),
      count(TStatus.Failure),
      count(TStatus.Error),
      count(TStatus.Skipped),
      count(TStatus.Ignored),
      count(TStatus.Canceled),
      count(TStatus.Pending)
    )
  }

  val Error: SuiteResult = new SuiteResult(TestResult.Error, 0, 0, 0, 0, 0, 0, 0)
  val Empty: SuiteResult = new SuiteResult(TestResult.Passed, 0, 0, 0, 0, 0, 0, 0)

}

abstract class TestEvent {
  def result: Option[TestResult]
  def detail: Seq[TEvent] = Nil
}
object TestEvent {
  def apply(events: Seq[TEvent]): TestEvent =
    new TestEvent {
      val result = Some(overallResult(events))
      override val detail = events
    }

  private[sbt] def overallResult(events: Seq[TEvent]): TestResult =
    ((TestResult.Passed: TestResult) /: events) { (sum, event) =>
      (sum, event.status) match {
        case (TestResult.Error, _)  => TestResult.Error
        case (_, TStatus.Error)     => TestResult.Error
        case (TestResult.Failed, _) => TestResult.Failed
        case (_, TStatus.Failure)   => TestResult.Failed
        case _                      => TestResult.Passed
      }
    }
}
