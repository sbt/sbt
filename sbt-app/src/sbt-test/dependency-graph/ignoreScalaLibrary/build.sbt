ThisBuild / scalaVersion := "2.12.17"

libraryDependencies ++= Seq(
  "org.slf4j" % "slf4j-api" % "1.7.2",
  "ch.qos.logback" % "logback-classic" % "1.0.7"
)

TaskKey[Unit]("check") := {
  val report = updateFull.value
  val graph = (Test / dependencyTree / asString).value
  def sanitize(str: String): String = str.split('\n').drop(1).map(_.trim).mkString("\n")
  val expectedGraph =
    """default:default-e95e05_2.12:0.1-SNAPSHOT [S]
      |  +-ch.qos.logback:logback-classic:1.0.7
      |  | +-ch.qos.logback:logback-core:1.0.7
      |  | +-org.slf4j:slf4j-api:1.6.6 (evicted by: 1.7.2)
      |  | +-org.slf4j:slf4j-api:1.7.2
      |  |
      |  +-org.slf4j:slf4j-api:1.7.2
      |  """.stripMargin
  IO.writeLines(file("/tmp/blib"), sanitize(graph).split("\n"))
  IO.writeLines(file("/tmp/blub"), sanitize(expectedGraph).split("\n"))
  require(sanitize(graph) == sanitize(expectedGraph), "Graph for report %s was '\n%s' but should have been '\n%s'" format (report, sanitize(graph), sanitize(expectedGraph)))
  ()
}
