import scala.collection.mutable.ListBuffer

ThisBuild / scalaVersion := "2.9.2"
ThisBuild / version := "0.1-SNAPSHOT"

lazy val justATransiviteDependencyEndpointProject = project

lazy val justATransitiveDependencyProject = project
  .dependsOn(justATransiviteDependencyEndpointProject)

lazy val justADependencyProject = project

lazy val test_project = project
  .dependsOn(justADependencyProject, justATransitiveDependencyProject)
  .settings(
    TaskKey[Unit]("check") := {
      val dotFile = (dependencyDot in Compile).value
      val expectedGraph =
        """digraph "dependency-graph" {
            |    graph[rankdir="LR"; splines=polyline]
            |    edge [
            |        arrowtail="none"
            |    ]
            |    "justadependencyproject:justadependencyproject_2.9.2:0.1-SNAPSHOT"[shape=box label=<justadependencyproject<BR/><B>justadependencyproject_2.9.2</B><BR/>0.1-SNAPSHOT> style="" penwidth="5" color="#B6E316"]
            |    "justatransitivedependencyproject:justatransitivedependencyproject_2.9.2:0.1-SNAPSHOT"[shape=box label=<justatransitivedependencyproject<BR/><B>justatransitivedependencyproject_2.9.2</B><BR/>0.1-SNAPSHOT> style="" penwidth="5" color="#0E92BE"]
            |    "justatransivitedependencyendpointproject:justatransivitedependencyendpointproject_2.9.2:0.1-SNAPSHOT"[shape=box label=<justatransivitedependencyendpointproject<BR/><B>justatransivitedependencyendpointproject_2.9.2</B><BR/>0.1-SNAPSHOT> style="" penwidth="5" color="#9EAD1B"]
            |    "test_project:test_project_2.9.2:0.1-SNAPSHOT"[shape=box label=<test_project<BR/><B>test_project_2.9.2</B><BR/>0.1-SNAPSHOT> style="" penwidth="5" color="#C37661"]
            |    "justatransitivedependencyproject:justatransitivedependencyproject_2.9.2:0.1-SNAPSHOT" -> "justatransivitedependencyendpointproject:justatransivitedependencyendpointproject_2.9.2:0.1-SNAPSHOT"
            |    "test_project:test_project_2.9.2:0.1-SNAPSHOT" -> "justadependencyproject:justadependencyproject_2.9.2:0.1-SNAPSHOT"
            |    "test_project:test_project_2.9.2:0.1-SNAPSHOT" -> "justatransitivedependencyproject:justatransitivedependencyproject_2.9.2:0.1-SNAPSHOT"
            |}
          """.stripMargin

      val graph: String = scala.io.Source.fromFile(dotFile.getAbsolutePath).mkString
      val errors = compareByLine(graph, expectedGraph)
      require(errors.isEmpty, errors.mkString("\n"))
      ()
    }
  )

def compareByLine(got: String, expected: String): Seq[String] = {
  val errors = ListBuffer[String]()
  got.split("\n").zip(expected.split("\n").toSeq).zipWithIndex.foreach {
    case ((got_line: String, expected_line: String), i: Int) =>
      if (got_line != expected_line) {
        errors.append("""not matching lines at line %s
          |expected: %s
          |got:      %s
          |""".stripMargin.format(i, expected_line, got_line))
      }
  }
  errors
}
