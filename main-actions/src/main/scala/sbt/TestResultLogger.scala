/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import sbt.Tests.{ Output, Summary }
import sbt.util.{ Level, Logger }
import sbt.protocol.testing.TestResult

/**
 * Logs information about tests after they finish.
 *
 * Log output can be customised by providing a specialised instance of this
 * trait via the `testResultLogger` setting.
 *
 * @since 0.13.5
 */
trait TestResultLogger {

  /**
   * Perform logging.
   *
   * @param log The target logger to write output to.
   * @param results The test results about which to log.
   * @param taskName The task about which we are logging. Eg. "my-module-b/test:test"
   */
  def run(log: Logger, results: Output, taskName: String): Unit

  /** Only allow invocation if certain criteria is met, else use another `TestResultLogger` (defaulting to nothing) . */
  final def onlyIf(f: (Output, String) => Boolean,
                   otherwise: TestResultLogger = TestResultLogger.Null) =
    TestResultLogger.choose(f, this, otherwise)

  /** Allow invocation unless a certain predicate passes, in which case use another `TestResultLogger` (defaulting to nothing) . */
  final def unless(f: (Output, String) => Boolean,
                   otherwise: TestResultLogger = TestResultLogger.Null) =
    TestResultLogger.choose(f, otherwise, this)
}

object TestResultLogger {

  /** A `TestResultLogger` that does nothing. */
  val Null = const(_ => ())

  /** SBT's default `TestResultLogger`. Use `copy()` to change selective portions. */
  val Default = Defaults.Main()

  /** Twist on the default which is completely silent when the subject module doesn't contain any tests. */
  def SilentWhenNoTests = silenceWhenNoTests(Default)

  /** Creates a `TestResultLogger` using a given function. */
  def apply(f: (Logger, Output, String) => Unit): TestResultLogger =
    new TestResultLogger {
      override def run(log: Logger, results: Output, taskName: String) =
        f(log, results, taskName)
    }

  /** Creates a `TestResultLogger` that ignores its input and always performs the same logging. */
  def const(f: Logger => Unit) = apply((l, _, _) => f(l))

  /**
   * Selects a `TestResultLogger` based on a given predicate.
   *
   * @param t The `TestResultLogger` to choose if the predicate passes.
   * @param f The `TestResultLogger` to choose if the predicate fails.
   */
  def choose(cond: (Output, String) => Boolean, t: TestResultLogger, f: TestResultLogger) =
    TestResultLogger((log, results, taskName) =>
      (if (cond(results, taskName)) t else f).run(log, results, taskName))

  /** Transforms the input to be completely silent when the subject module doesn't contain any tests. */
  def silenceWhenNoTests(d: Defaults.Main) =
    d.copy(
      printStandard = d.printStandard.unless((results, _) => results.events.isEmpty),
      printNoTests = Null
    )

  object Defaults {

    /** SBT's default `TestResultLogger`. Use `copy()` to change selective portions. */
    case class Main(
        printStandard_? : Output => Boolean = Defaults.printStandard_?,
        printSummary: TestResultLogger = Defaults.printSummary,
        printStandard: TestResultLogger = Defaults.printStandard,
        printFailures: TestResultLogger = Defaults.printFailures,
        printNoTests: TestResultLogger = Defaults.printNoTests
    ) extends TestResultLogger {

      override def run(log: Logger, results: Output, taskName: String): Unit = {
        def run(r: TestResultLogger): Unit = r.run(log, results, taskName)

        run(printSummary)

        if (printStandard_?(results))
          run(printStandard)

        if (results.events.isEmpty)
          run(printNoTests)
        else
          run(printFailures)

        results.overall match {
          case TestResult.Error | TestResult.Failed => throw new TestsFailedException
          case TestResult.Passed                    =>
        }
      }
    }

    val printSummary = TestResultLogger((log, results, _) => {
      val multipleFrameworks = results.summaries.size > 1
      for (Summary(name, message) <- results.summaries)
        if (message.isEmpty)
          log.debug("Summary for " + name + " not available.")
        else {
          if (multipleFrameworks) log.info(name)
          log.info(message)
        }
    })

    val printStandard_? : Output => Boolean =
      results =>
        // Print the standard one-liner statistic if no framework summary is defined, or when > 1 framework is in used.
        results.summaries.size > 1 || results.summaries.headOption.forall(_.summaryText.isEmpty)

    val printStandard = TestResultLogger((log, results, _) => {
      val (skippedCount,
           errorsCount,
           passedCount,
           failuresCount,
           ignoredCount,
           canceledCount,
           pendingCount) =
        results.events.foldLeft((0, 0, 0, 0, 0, 0, 0)) {
          case ((skippedAcc, errorAcc, passedAcc, failureAcc, ignoredAcc, canceledAcc, pendingAcc),
                (name @ _, testEvent)) =>
            (skippedAcc + testEvent.skippedCount,
             errorAcc + testEvent.errorCount,
             passedAcc + testEvent.passedCount,
             failureAcc + testEvent.failureCount,
             ignoredAcc + testEvent.ignoredCount,
             canceledAcc + testEvent.canceledCount,
             pendingAcc + testEvent.pendingCount)
        }
      val totalCount = failuresCount + errorsCount + skippedCount + passedCount
      val base =
        s"Total $totalCount, Failed $failuresCount, Errors $errorsCount, Passed $passedCount"

      val otherCounts = Seq("Skipped" -> skippedCount,
                            "Ignored" -> ignoredCount,
                            "Canceled" -> canceledCount,
                            "Pending" -> pendingCount)
      val extra = otherCounts.filter(_._2 > 0).map { case (label, count) => s", $label $count" }

      val postfix = base + extra.mkString
      results.overall match {
        case TestResult.Error  => log.error("Error: " + postfix)
        case TestResult.Passed => log.info("Passed: " + postfix)
        case TestResult.Failed => log.error("Failed: " + postfix)
      }
    })

    val printFailures = TestResultLogger((log, results, _) => {
      def select(resultTpe: TestResult) = results.events collect {
        case (name, tpe) if tpe.result == resultTpe =>
          scala.reflect.NameTransformer.decode(name)
      }

      def show(label: String, level: Level.Value, tests: Iterable[String]): Unit =
        if (tests.nonEmpty) {
          log.log(level, label)
          log.log(level, tests.mkString("\t", "\n\t", ""))
        }

      show("Passed tests:", Level.Debug, select(TestResult.Passed))
      show("Failed tests:", Level.Error, select(TestResult.Failed))
      show("Error during tests:", Level.Error, select(TestResult.Error))
    })

    val printNoTests = TestResultLogger(
      (log, results, taskName) => log.info("No tests to run for " + taskName))
  }
}
