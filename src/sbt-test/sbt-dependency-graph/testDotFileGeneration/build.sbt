import collection.mutable.ListBuffer

def defaultSettings = Seq(scalaVersion := "2.12.3")

lazy val `just-a-transitive-dependency-endpoint` =
  project
    .in(file("a"))
    .settings(defaultSettings)

lazy val `just-a-transitive-dependency` =
  project
    .in(file("b"))
    .settings(defaultSettings)
    .dependsOn(`just-a-transitive-dependency-endpoint`)

lazy val `just-a-dependency` =
  project
    .in(file("c"))
    .settings(defaultSettings)

lazy val `test-dot-file-generation` =
  project
    .in(file("d"))
    .settings(defaultSettings)
    .settings(
      TaskKey[Unit]("check") := {
        val dotFile = (dependencyDot in Compile).value
        val scalaV = scalaBinaryVersion.value

        val expectedGraph =
          s"""|digraph "dependency-graph" {
              |    graph[rankdir="LR"]
              |    edge [
              |        arrowtail="none"
              |    ]
              |    "test-dot-file-generation:test-dot-file-generation_${scalaV}:0.1-SNAPSHOT"[label=<test-dot-file-generation<BR/><B>test-dot-file-generation_${scalaV}</B><BR/>0.1-SNAPSHOT> style=""]
              |    "just-a-transitive-dependency:just-a-transitive-dependency_${scalaV}:0.1-SNAPSHOT"[label=<just-a-transitive-dependency<BR/><B>just-a-transitive-dependency_${scalaV}</B><BR/>0.1-SNAPSHOT> style=""]
              |    "just-a-transitive-dependency-endpoint:just-a-transitive-dependency-endpoint_${scalaV}:0.1-SNAPSHOT"[label=<just-a-transitive-dependency-endpoint<BR/><B>just-a-transitive-dependency-endpoint_${scalaV}</B><BR/>0.1-SNAPSHOT> style=""]
              |    "just-a-dependency:just-a-dependency_${scalaV}:0.1-SNAPSHOT"[label=<just-a-dependency<BR/><B>just-a-dependency_${scalaV}</B><BR/>0.1-SNAPSHOT> style=""]
              |    "test-dot-file-generation:test-dot-file-generation_${scalaV}:0.1-SNAPSHOT" -> "just-a-transitive-dependency:just-a-transitive-dependency_${scalaV}:0.1-SNAPSHOT"
              |    "just-a-transitive-dependency:just-a-transitive-dependency_${scalaV}:0.1-SNAPSHOT" -> "just-a-transitive-dependency-endpoint:just-a-transitive-dependency-endpoint_${scalaV}:0.1-SNAPSHOT"
              |    "test-dot-file-generation:test-dot-file-generation_${scalaV}:0.1-SNAPSHOT" -> "just-a-dependency:just-a-dependency_${scalaV}:0.1-SNAPSHOT"
              |}""".stripMargin

        val graph : String = scala.io.Source.fromFile(dotFile.getAbsolutePath).mkString
        val errors = compareByLine(graph, expectedGraph)
        require(errors.isEmpty , errors.mkString("\n"))
        ()
      }
    )
    .dependsOn(`just-a-dependency`, `just-a-transitive-dependency`)
    
def compareByLine(got : String, expected : String) : Seq[String] = {
  val errors = ListBuffer[String]()
  got.split("\n").zip(expected.split("\n").toSeq).zipWithIndex.foreach { case((got_line : String, expected_line : String), i : Int) =>
    if(got_line != expected_line) {
      errors.append(
        """not matching lines at line %s
          |expected: %s
          |got:      %s
          |""".stripMargin.format(i,expected_line, got_line))
    }
  }
  errors
}