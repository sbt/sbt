import Dependencies._
import sbt.internal.CommandStrings.{ inspectBrief, inspectDetailed }
import sbt.internal.Inspect

lazy val root = (project in file("."))
  .settings(
    Global / cancelable := true,
    ThisBuild / scalaVersion := "2.12.3",
    console / scalacOptions += "-deprecation",
    Compile / console / scalacOptions += "-Ywarn-numeric-widen",
    projA / Compile / console / scalacOptions += "-feature",
    Zero / Zero / name := "foo",

    libraryDependencies += uTest % Test,
    testFrameworks += new TestFramework("utest.runner.Framework"),

    commands += Command("inspectCheck", inspectBrief, inspectDetailed)(Inspect.parser) {
      case (s, (option, sk)) =>
        val actual = Inspect.output(s, option, sk)
        val expected = s"""Task: Unit
Description:
\tExecutes all tests.
Provided by:
\tProjectRef(uri("${baseDirectory.value.toURI}"), "root") / Test / test
Defined at:
\t(sbt.Defaults.testTasks) Defaults.scala:670
Dependencies:
\tTest / executeTests
\tTest / test / streams
\tTest / state
\tTest / test / testResultLogger
Delegates:
\tTest / test
\tRuntime / test
\tCompile / test
\ttest
\tThisBuild / Test / test
\tThisBuild / Runtime / test
\tThisBuild / Compile / test
\tThisBuild / test
\tZero / Test / test
\tZero / Runtime / test
\tZero / Compile / test
\tGlobal / test
Related:
\tprojA / Test / test"""

        if (processText(actual) == processText(expected)) ()
        else {
          sys.error(s"""actual:
$actual

expected:
$expected

diff:
""" +
(
  processText(actual)
    zip processText(expected)
    filter { case ( a, b) => a != b }
))
        }
        s.log.info(actual)
        s
    }
  )

lazy val projA = (project in file("a"))

def processText(s: String): Vector[String] = {
  val xs = s.split(IO.Newline).toVector
    .map( _.trim )
    // declared location of the task is unstable.
    .filterNot( _.contains("Defaults.scala") )
  xs
}
