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
 *
 * @param throwables
 *   The exceptions thrown by the tests in this suite, as live objects. They are needed as objects
 *   rather than as rendered text because the ClassLoaderLayeringStrategy diagnostic in `Defaults`
 *   inspects them with `isInstanceOf` and walks `getCause` to recognise `NoClassDefFoundError` and
 *   friends.
 *
 *   RETENTION HAZARD: a `Throwable`'s backtrace references the `Class` objects of every frame, and
 *   a `Class` strongly references its defining class loader. Holding a `SuiteResult` with a
 *   non-empty `throwables` therefore keeps the test class loader -- and every jar handle it has
 *   open -- alive. That is fine for the duration of the test task, which is where these are
 *   consumed, but anything that outlives the task must call [[withoutThrowables]] first.
 *   `TestSummary.append` does exactly that before stashing a copy on `State.attributes`; see the
 *   note on `TestSummary.entriesKey`.
 *   On Windows a leaked handle makes the underlying jar undeletable (e.g. by `clearCaches`).
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

  /**
   * Returns an equivalent result without retaining test-thrown exceptions.
   *
   * Use this before retaining test results beyond the lifetime of the test task. See
   * `throwables` for why retaining those exceptions can keep the test class loader alive.
   */
  def withoutThrowables: SuiteResult =
    if throwables.isEmpty then this
    else
      new SuiteResult(
        result,
        passedCount,
        failureCount,
        errorCount,
        skippedCount,
        ignoredCount,
        canceledCount,
        pendingCount,
      )

  def +(other: SuiteResult): SuiteResult = {
    val combinedTestResult =
      (result, other.result) match {
        case (TestResult.Empty, TestResult.Empty) => TestResult.Empty: TestResult
        case (TestResult.Passed | TestResult.Empty, TestResult.Passed | TestResult.Empty) =>
          TestResult.Passed: TestResult
        case (_, TestResult.Error) => TestResult.Error: TestResult
        case (TestResult.Error, _) => TestResult.Error: TestResult
        case _                     => TestResult.Failed: TestResult
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
