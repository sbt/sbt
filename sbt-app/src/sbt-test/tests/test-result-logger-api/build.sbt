import sbt.*
import sbt.Tests.Output
import sbt.util.Logger

ThisBuild / scalaVersion := "3.8.4"

val marker = file("test-result-logger-ran")
val verify = "com.eed3si9n.verify" %% "verify" % "1.0.0"

libraryDependencies += verify % Test
testFrameworks += new TestFramework("verify.runner.Framework")

Test / testResultLogger := new TestResultLogger:
  def run(log: Logger, results: Output, taskName: String): Unit =
    val suiteResults: Iterable[SuiteResult] =
      results.withoutThrowables.events.values.map(_.withoutThrowables)
    val suite = suiteResults.head
    IO.write(
      marker,
      Seq(
        s"overall=${results.overall}",
        s"suites=${suiteResults.size}",
        s"suiteResult=${suite.result}",
        s"passed=${suite.passedCount}",
        s"failed=${suite.failureCount}",
        s"errors=${suite.errorCount}"
      ).mkString("", "\n", "\n")
    )
