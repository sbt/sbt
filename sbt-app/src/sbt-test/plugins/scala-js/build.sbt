ThisBuild / scalaVersion := "2.13.18"

@transient
lazy val checkReports = taskKey[Unit]("checks that each JUnit XML report has its test case")

@transient
lazy val checkDigests = taskKey[Unit]("checks that the test digests include each test suite")

lazy val root = (project in file("."))
  .enablePlugins(ScalaJSPlugin, ScalaJSJUnitPlugin)
  .settings(
    libraryDependencies += "org.scala-js" %% "scalajs-java-time" % "1.0.0",
    Test / parallelExecution := false,
    checkDigests := {
      val digests = (Test / definedTestDigests).value
      assert(digests.contains("example.AlphaTest"), s"missing example.AlphaTest in $digests")
      assert(digests.contains("example.BetaTest"), s"missing example.BetaTest in $digests")
    },
    checkReports := {
      val dir = (Test / testReportsDirectory).value
      for suite <- Seq("example.AlphaTest", "example.BetaTest") do
        val xml = IO.read(dir / s"TEST-$suite.xml")
        assert(xml.contains("tests=\"1\""), s"$suite report has no test case:\n$xml")
    }
  )
