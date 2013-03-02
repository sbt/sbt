import net.virtualvoid.sbt.graph.Plugin._

graphSettings

scalaVersion := "2.9.2"

libraryDependencies ++= Seq(
  "com.codahale" % "jerkson_2.9.1" % "0.5.0"
)

TaskKey[Unit]("check") <<= (ivyReport in Test, asciiTree in Test) map { (report, graph) =>
  def sanitize(str: String): String = str.split('\n').drop(1).map(_.trim).mkString("\n")
  val expectedGraph =
    """default:default-dbc48d_2.9.2:0.1-SNAPSHOT [S]
      |  +-com.codahale:jerkson_2.9.1:0.5.0 [S]
      |    +-org.codehaus.jackson:jackson-core-asl:1.9.12
      |    +-org.codehaus.jackson:jackson-mapper-asl:1.9.12
      |      +-org.codehaus.jackson:jackson-core-asl:1.9.12
      |  """.stripMargin
  IO.writeLines(file("/tmp/blib"), sanitize(graph).split("\n"))
  IO.writeLines(file("/tmp/blub"), sanitize(expectedGraph).split("\n"))
  require(sanitize(graph) == sanitize(expectedGraph), "Graph for report %s was '\n%s' but should have been '\n%s'" format (report, sanitize(graph), sanitize(expectedGraph)))
  ()
}
