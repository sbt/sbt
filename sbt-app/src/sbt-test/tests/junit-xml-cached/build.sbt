import scala.xml.XML

val checkReport = inputKey[Unit]("Checks the report and whether it is marked as cached")

lazy val root = (project in file(".")).settings(
  scalaVersion := "3.8.4",
  libraryDependencies += "org.scalameta" %% "munit" % "1.3.6" % Test,
  checkReport := {
    val expectCached = complete.DefaultParsers.spaceDelimited("<arg>").parsed.head.toBoolean
    val report = XML.loadFile((Test / testReportsDirectory).value / "TEST-ExampleSuite.xml")
    assert((report \ "testcase").map(_ \@ "name") == Seq("adds"))
    val cached = (report \ "properties" \ "property").exists(p =>
      (p \@ "name") == "sbt.cached" && (p \@ "value") == "true"
    )
    assert(cached == expectCached, s"expected cached = $expectCached")
  },
)
