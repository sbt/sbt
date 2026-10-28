ThisBuild / scalaVersion := "2.13.18"

@transient
lazy val checkReports = taskKey[Unit]("checks that each JUnit XML report has its test case")

lazy val root = (project in file("."))
  .enablePlugins(ScalaJSPlugin, ScalaJSJUnitPlugin)
  .settings(
    libraryDependencies += "org.scala-js" %% "scalajs-java-time" % "1.0.0",
    Test / parallelExecution := false,
    checkReports := {
      val dir = (Test / testReportsDirectory).value
      for suite <- Seq("example.AlphaTest", "example.BetaTest") do
        val xml = IO.read(dir / s"TEST-$suite.xml")
        assert(xml.contains("tests=\"1\""), s"$suite report has no test case:\n$xml")
    }
  )
