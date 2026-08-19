/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import sbt.Tests.Output
import sbt.internal.util.Terminal
import sbt.protocol.testing.TestResult
import sbt.util.{ Level, Logger }

/**
 * Logs information about tests after they finish.
 *
 * Log output can be customised by providing a specialised instance of this
 * trait via the `testResultLogger` setting.
 *
 * @since 0.13.5
 */
trait TestResultLogger:

  /**
   * Perform logging.
   *
   * @param log The target logger to write output to.
   * @param results The test results about which to log.
   * @param taskName The task about which we are logging. Eg. "my-module-b/Test/test"
   */
  def run(log: Logger, results: Output, taskName: String): Unit

  def run(log: Logger, results: Output, taskName: String, cached: Vector[String]): Unit =
    run(log, results, taskName)

  def summary(log: Logger, entries: Vector[(Output, String, Vector[String])]): Unit =
    entries.foreach { case (results, taskName, _) =>
      run(log, results, taskName)
    }

  /** Only allow invocation if certain criteria is met, else use another `TestResultLogger` (defaulting to nothing) . */
  final def onlyIf(
      f: (Output, String) => Boolean,
      otherwise: TestResultLogger = TestResultLogger.Null
  ) =
    TestResultLogger.choose(f, this, otherwise)

  /** Allow invocation unless a certain predicate passes, in which case use another `TestResultLogger` (defaulting to nothing) . */
  final def unless(
      f: (Output, String) => Boolean,
      otherwise: TestResultLogger = TestResultLogger.Null
  ) =
    TestResultLogger.choose(f, otherwise, this)
end TestResultLogger

object TestResultLogger:

  /** A `TestResultLogger` that does nothing. */
  val Null = const(_ => ())

  /** sbt's default `TestResultLogger`. Use `copy()` to change selective portions. */
  val Default = Defaults.Main()

  /** Twist on the default which is completely silent when the subject module doesn't contain any tests. */
  def SilentWhenNoTests = silenceWhenNoTests(Default)

  /** Creates a `TestResultLogger` using a given function. */
  def apply(f: (Logger, Output, String) => Unit): TestResultLogger =
    (log, results, taskName) => f(log, results, taskName)

  /** Creates a `TestResultLogger` using a given function that also receives the cached suite names. */
  def apply(f: (Logger, Output, String, Vector[String]) => Unit): TestResultLogger =
    new TestResultLogger:
      def run(log: Logger, results: Output, taskName: String): Unit =
        f(log, results, taskName, Vector.empty)
      override def run(
          log: Logger,
          results: Output,
          taskName: String,
          cached: Vector[String]
      ): Unit =
        f(log, results, taskName, cached)

  /** Creates a `TestResultLogger` that ignores its input and always performs the same logging. */
  def const(f: Logger => Unit) = apply((l, _, _) => f(l))

  /**
   * Selects a `TestResultLogger` based on a given predicate.
   *
   * @param t The `TestResultLogger` to choose if the predicate passes.
   * @param f The `TestResultLogger` to choose if the predicate fails.
   */
  def choose(cond: (Output, String) => Boolean, t: TestResultLogger, f: TestResultLogger) =
    TestResultLogger((log, results, taskName, cached) =>
      (if cond(results, taskName) then t else f).run(log, results, taskName, cached)
    )

  /** Transforms the input to be completely silent when the subject module doesn't contain any tests. */
  def silenceWhenNoTests(d: Defaults.Main) =
    d.copy(
      printStandard = d.printStandard.unless((results, _) => results.events.isEmpty),
      printNoTests = Null
    )

  object Defaults:
    private val suitePadding = " " * 8

    private[sbt] enum SummaryStatus:
      case Passed, Failed, Errored

      def label: String = this match
        case SummaryStatus.Passed  => "passed"
        case SummaryStatus.Failed  => "failed"
        case SummaryStatus.Errored => "error"

      def word: String = this match
        case SummaryStatus.Passed  => "succeeded"
        case SummaryStatus.Failed  => "failed"
        case SummaryStatus.Errored => "errored"
    end SummaryStatus

    private[sbt] enum SuiteStatus:
      case Pass, CachedPass, Fail, Error

      def isPassing: Boolean = this match
        case SuiteStatus.Pass | SuiteStatus.CachedPass => true
        case SuiteStatus.Fail | SuiteStatus.Error      => false

      def render(isColorEnabled: Boolean): String =
        val (prefix, word, color) = this match
          case SuiteStatus.Pass       => ("", "PASS", scala.Console.GREEN)
          case SuiteStatus.CachedPass => ("(cached) ", "PASS", scala.Console.GREEN)
          case SuiteStatus.Fail       => ("", "FAIL", scala.Console.RED)
          case SuiteStatus.Error      => ("", "ERROR", scala.Console.RED)
        if isColorEnabled then s"$prefix$color$word${scala.Console.RESET}" else s"$prefix$word"
    end SuiteStatus

    private[sbt] object SuiteStatus:
      def apply(r: TestResult): SuiteStatus = r match
        case TestResult.Passed | TestResult.Empty => SuiteStatus.Pass
        case TestResult.Failed                    => SuiteStatus.Fail
        case TestResult.Error                     => SuiteStatus.Error

    /** sbt's default `TestResultLogger`. Use `copy()` to change selective portions. */
    case class Main(
        printStandard_? : Output => Boolean = Defaults.printStandard_?,
        printSummary: TestResultLogger = Defaults.printSummary,
        printStandard: TestResultLogger = Defaults.printStandard,
        printFailures: TestResultLogger = Defaults.printFailures,
        printNoTests: TestResultLogger = Defaults.printNoTests
    ) extends TestResultLogger {

      override def run(log: Logger, results: Output, taskName: String): Unit =
        run(log, results, taskName, Vector.empty)

      override def run(
          log: Logger,
          results: Output,
          taskName: String,
          cached: Vector[String]
      ): Unit = {
        def run(r: TestResultLogger): Unit = r.run(log, results, taskName, cached)

        run(printSummary)

        if (printStandard_?(results))
          run(printStandard)

        if (results.events.isEmpty)
          run(printNoTests)
        else
          run(printFailures)

        // Logging only. Failure propagation lives in the task wrapper
        // (`Defaults.testFull` / `inputTests0`) so the cross-project recap
        // (sbt/sbt#2998) can attach the task name and `Tests.Output` to
        // the `TestsFailedException` thrown there. The trait contract is
        // "perform logging"; it does not document throwing on failure.
        ()
      }
    }

    val printSummary = TestResultLogger((log, results, _) => {
      val multipleFrameworks = results.summaries.size > 1
      for Tests.Summary(name, message) <- results.summaries do
        if (message.isEmpty) log.debug("Summary for " + name + " not available.")
        else {
          if (multipleFrameworks) log.info(name)
          log.info(message)
        }
    })

    val printStandard_? : Output => Boolean =
      results =>
        // Print the standard one-liner statistic if no framework summary is defined, or when > 1 framework is in used.
        results.summaries.size > 1 || results.summaries.headOption.forall(_.summaryText.isEmpty)

    val printStandard = TestResultLogger((log, results, _, cached) => {
      val counts = countsString(results.events.values, cached.size, true)
      results.overall match
        case TestResult.Empty  => ()
        case TestResult.Error  => log.error(s"${SummaryStatus.Errored.label}: $counts")
        case TestResult.Passed => log.info(s"${SummaryStatus.Passed.label}: $counts")
        case TestResult.Failed => log.error(s"${SummaryStatus.Failed.label}: $counts")
    })

    private[sbt] def countsString(events: Iterable[SuiteResult]): String =
      countsString(events, 0, false)

    /**
     * Renders suite counts as a single line like `total 10, failed 2, errors
     * 0, passed 8`. Counts suites (classes/objects), not individual test examples.
     */
    private[sbt] def countsString(
        events: Iterable[SuiteResult],
        cachedCount: Int,
        alwaysShowCached: Boolean,
    ): String = {
      val (failuresCount, errorsCount, passedCount) =
        events.foldLeft((0, 0, 0)) { case ((failureAcc, errorAcc, passedAcc), suite) =>
          suite.result match
            case TestResult.Failed                    => (failureAcc + 1, errorAcc, passedAcc)
            case TestResult.Error                     => (failureAcc, errorAcc + 1, passedAcc)
            case TestResult.Passed | TestResult.Empty => (failureAcc, errorAcc, passedAcc + 1)
        }
      val totalCount = failuresCount + errorsCount + passedCount + cachedCount
      val base =
        s"total $totalCount, failed $failuresCount, errors $errorsCount, passed ${passedCount + cachedCount}"
      val cachedField =
        if cachedCount > 0 || alwaysShowCached then s", cached $cachedCount" else ""
      base + cachedField
    }

    val printFailures = TestResultLogger((log, results, _) => {
      def select(resultTpe: TestResult) = results.events collect {
        case (name, tpe) if tpe.result == resultTpe =>
          scala.reflect.NameTransformer.decode(name)
      }

      def show(label: String, level: Level.Value, tests: Iterable[String]): Unit =
        if (tests.nonEmpty) {
          log.log(level, label)
          log.log(level, tests.mkString(suitePadding, s"\n$suitePadding", ""))
        }

      show("passed tests:", Level.Debug, select(TestResult.Passed))
      show("failed tests:", Level.Error, select(TestResult.Failed))
      show("error during tests:", Level.Error, select(TestResult.Error))
    })

    val printNoTests = TestResultLogger((log, results, taskName, cached) =>
      val suffix = if cached.nonEmpty then s" (${cached.size} cached)" else ""
      log.debug(s"no tests to run for $taskName$suffix")
    )

    /**
     * Renders a cross-project aggregate summary at the end of an aggregated
     * run, in the style selected by `mode` (see `TestSummary`). Per-task
     * logging is unchanged (delegates to `Default`); `summary` is where this
     * differs from a plain `TestResultLogger`.
     */
    case class Summary(mode: TestSummary) extends TestResultLogger:
      override def run(log: Logger, results: Output, taskName: String): Unit =
        Default.run(log, results, taskName)

      override def summary(log: Logger, entries: Vector[(Output, String, Vector[String])]): Unit =
        val lines = Summary.render(mode, entries, Terminal.get.isColorEnabled)
        val logLine: String => Unit = Summary.overallStatus(entries) match
          case Some(SummaryStatus.Errored) | Some(SummaryStatus.Failed) => log.error(_)
          case _                                                        => log.info(_)
        lines.foreach(line => logLine(if line.isEmpty then " " else line))
    end Summary

    object Summary:
      private val columnGap = 5

      def apply(): Summary = Summary(mode = TestSummary.default)

      /**
       * The rendered summary; empty when nothing ran. Both styles end with an
       * aggregate line, e.g. `passed: total 4, failed 0, errors 0, passed 4,
       * cached 2`. Cached classes count into `total` and `passed`, each as one
       * (their case counts are unknown without running them).
       */
      private[sbt] def render(
          mode: TestSummary,
          entries: Vector[(Output, String, Vector[String])],
          isColorEnabled: Boolean,
      ): Vector[String] =
        overallStatus(entries) match
          case None         => Vector.empty
          case Some(status) =>
            val executed = entries.flatMap(_._1.events.values)
            val cached = entries.map(_._3.size).sum
            val counts =
              s"${status.label}: ${countsString(executed, cached, alwaysShowCached = true)}"
            def withCounts(detailLines: Vector[String]): Vector[String] =
              if detailLines.isEmpty then Vector(counts) else detailLines :+ "" :+ counts
            mode match
              case TestSummary.None    => Vector.empty
              case TestSummary.Failure =>
                withCounts(detail(status, entries, failuresOnly = true, isColorEnabled))
              case TestSummary.Success =>
                withCounts(detail(status, entries, failuresOnly = false, isColorEnabled))

      /** The overall status across `entries`, or `None` when nothing ran. */
      private def overallStatus(
          entries: Vector[(Output, String, Vector[String])]
      ): Option[SummaryStatus] =
        val executed = entries.flatMap(_._1.events.values)
        val cached = entries.map(_._3.size).sum
        if executed.isEmpty && cached == 0 then None
        else if executed.exists(_.result == TestResult.Error) then Some(SummaryStatus.Errored)
        else if executed.exists(_.result == TestResult.Failed) then Some(SummaryStatus.Failed)
        else Some(SummaryStatus.Passed)

      private def detail(
          status: SummaryStatus,
          entries: Vector[(Output, String, Vector[String])],
          failuresOnly: Boolean,
          isColorEnabled: Boolean,
      ): Vector[String] =
        val tasks = entries
          .map: (output, taskName, cachedNames) =>
            val executed = output.events.view.mapValues(s => SuiteStatus(s.result)).toVector
            val suites =
              if failuresOnly then executed.filter { case (_, st) => !st.isPassing }
              else executed ++ cachedNames.map(_ -> SuiteStatus.CachedPass)
            taskName -> suites.sortBy(_._1)
          .filter(_._2.nonEmpty)
          .sortBy(_._1)
        if tasks.isEmpty then Vector.empty
        else
          val headerCount =
            if status == SummaryStatus.Passed then tasks.size
            else entries.count(_._1.events.values.exists(s => !SuiteStatus(s.result).isPassing))
          val plural = if headerCount == 1 then "" else "s"
          val width = tasks.flatMap(_._2.map(_._1.length)).max + columnGap
          val lines = Vector.newBuilder[String]
          lines += s"Test summary ($headerCount test task$plural ${status.word}):"
          tasks.foreach: (taskName, suites) =>
            lines += s"  $taskName"
            suites.foreach: (name, st) =>
              lines += s"${suitePadding}${name.padTo(width, ' ')}${st.render(isColorEnabled)}"
          lines.result()
    end Summary
  end Defaults
end TestResultLogger
