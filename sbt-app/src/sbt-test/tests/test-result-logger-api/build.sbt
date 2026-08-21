import sbt.*
import sbt.Tests.Output
import sbt.util.Logger

ThisBuild / scalaVersion := "3.8.4"

val marker = file("test-result-logger-ran")

Test / testResultLogger := new TestResultLogger:
  def run(log: Logger, results: Output, taskName: String): Unit =
    val suiteResults: Iterable[SuiteResult] = results.events.values
    IO.write(marker, s"$taskName:${results.overall}:${suiteResults.size}")
