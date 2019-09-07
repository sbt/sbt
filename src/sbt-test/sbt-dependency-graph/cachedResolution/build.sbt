scalaVersion := "2.12.9"

libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.28"
updateOptions := updateOptions.value.withCachedResolution(true)

TaskKey[Unit]("check") := {
  val report = (ivyReport in Test).value
  val graph = (asciiTree in Test).value

  def sanitize(str: String): String = str.split('\n').drop(1).mkString("\n")
  val expectedGraph =
    """default:cachedresolution_2.12:0.1.0-SNAPSHOT
      |  +-org.slf4j:slf4j-api:1.7.28
      |  """.stripMargin
  require(sanitize(graph) == sanitize(expectedGraph), "Graph for report %s was '\n%s' but should have been '\n%s'" format (report, sanitize(graph), sanitize(expectedGraph)))
  ()
}
