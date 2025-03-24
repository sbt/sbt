/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import testing.{ Logger as TLogger, Event as TEvent, Status as TStatus }
import sbt.protocol.testing.*

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
  def contentLogger(@deprecated("unused", "") test: TestDefinition): Option[ContentLogger] = None

}

final class ContentLogger(val log: TLogger, val flush: () => Unit)

trait TestsListener extends TestReportListener {

  /** called once, at beginning. */
  def doInit(): Unit

  /** called once, at end of the test group. */
  def doComplete(finalResult: TestResult): Unit

}

/**
 * Provides the overall `result` of a group of tests (a suite) and test counts for each result type.
 */
final class SuiteResult(
    val result: TestResult,
    val passedCount: Int,
    val failureCount: Int,
    val errorCount: Int,
    val skippedCount: Int,
    val ignoredCount: Int,
    val canceledCount: Int,
    val pendingCount: Int,
    val throwables: Seq[Throwable]
) {
  def this(
      result: TestResult,
      passedCount: Int,
      failureCount: Int,
      errorCount: Int,
      skippedCount: Int,
      ignoredCount: Int,
      canceledCount: Int,
      pendingCount: Int,
  ) =
    this(
      result,
      passedCount,
      failureCount,
      errorCount,
      skippedCount,
      ignoredCount,
      canceledCount,
      pendingCount,
      Nil
    )
  def +(other: SuiteResult): SuiteResult = {
    val combinedTestResult =
      (result, other.result) match {
        case (TestResult.Passed, TestResult.Passed) => TestResult.Passed: TestResult
        case (_, TestResult.Error)                  => TestResult.Error: TestResult
        case (TestResult.Error, _)                  => TestResult.Error: TestResult
        case _                                      => TestResult.Failed: TestResult
      }
    new SuiteResult(
      combinedTestResult,
      passedCount + other.passedCount,
      failureCount + other.failureCount,
      errorCount + other.errorCount,
      skippedCount + other.skippedCount,
      ignoredCount + other.ignoredCount,
      canceledCount + other.canceledCount,
      pendingCount + other.pendingCount,
      throwables ++ other.throwables
    )
  }
}

object SuiteResult {

  /**
   * Computes the overall result and counts for a suite with individual test results in `events`.
   */
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
      count(TStatus.Pending),
      events.collect { case e if e.throwable.isDefined => e.throwable.get }
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
    events.foldLeft(TestResult.Passed: TestResult) { (sum, event) =>
      (sum, event.status) match {
        case (TestResult.Error, _)  => TestResult.Error
        case (_, TStatus.Error)     => TestResult.Error
        case (TestResult.Failed, _) => TestResult.Failed
        case (_, TStatus.Failure)   => TestResult.Failed
        case _                      => TestResult.Passed
      }
    }
}
