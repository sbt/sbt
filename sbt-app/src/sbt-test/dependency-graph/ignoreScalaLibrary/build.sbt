ThisBuild / scalaVersion := "2.12.21"

name := "foo"
libraryDependencies ++= Seq(
  "org.slf4j" % "slf4j-api" % "1.7.2",
  "ch.qos.logback" % "logback-classic" % "1.0.7"
)
csrMavenDependencyOverride := false

TaskKey[Unit]("check") := {
  val report = updateFull.value
  val graph = (Test / dependencyTree).toTask(" --quiet").value
  def sanitize(str: String): String = str.linesIterator.map(_.trim).mkString("\n").trim

/*
Started to return:

ch.qos.logback:logback-core:1.0.7
default:sbt_8ae1da13_2.12:0.1.0-SNAPSHOT [S]
  +-ch.qos.logback:logback-classic:1.0.7
  | +-org.slf4j:slf4j-api:1.6.6 (evicted by: 1.7.2)
  |
  +-org.slf4j:slf4j-api:1.7.2
*/

  val expectedGraph =
    Seq(
      "ch.qos.logback:logback-core:1.0.7",
      "foo:foo_2.12:0.1.0-SNAPSHOT [S]",
      "+-ch.qos.logback:logback-classic:1.0.7",
      "| +-org.slf4j:slf4j-api:1.6.6 (evicted by: 1.7.2)",
      "|",
      "+-org.slf4j:slf4j-api:1.7.2",
      ""
    ).mkString("\n")


  // IO.writeLines(file("/tmp/blib"), sanitize(graph).split("\n"))
  // IO.writeLines(file("/tmp/blub"), sanitize(expectedGraph).split("\n"))
  require(sanitize(graph) == sanitize(expectedGraph), "Graph for report %s was '\n%s' but should have been '\n%s'" format (report, sanitize(graph), sanitize(expectedGraph)))
  ()
}
