import collection.mutable.ListBuffer
import net.virtualvoid.sbt.graph.Plugin._

import sbt._
import sbt.Keys._

object Build extends sbt.Build {

  def defaultSettings =
    seq(scalaVersion := "2.9.2")

  lazy val justATransiviteDependencyEndpointProject =
    Project("just-a-transitive-dependency-endpoint", file("."))
      .settings(defaultSettings: _*)

  lazy val justATransitiveDependencyProject =
    Project("just-a-transitive-dependency", file("."))
      .settings(defaultSettings: _*)
      .dependsOn(justATransiviteDependencyEndpointProject)

  lazy val justADependencyProject =
    Project("just-a-dependency", file("."))
      .settings(defaultSettings: _*)

  lazy val test_project =
    Project("test-dot-file-generation", file("."))
      .settings(graphSettings: _*)
      .settings(defaultSettings: _*)
      .settings(
        TaskKey[Unit]("check") <<= (dependencyDot in Compile) map { (dotFile) =>
          val expectedGraph =
            """digraph "dependency-graph" {
              |    graph[rankdir="LR"]
              |    node [
              |        shape="record"
              |    ]
              |    edge [
              |        arrowtail="none"
              |    ]
              |    "test-dot-file-generation:test-dot-file-generation_2.9.2:0.1-SNAPSHOT"[label=<test-dot-file-generation<BR/><B>test-dot-file-generation_2.9.2</B><BR/>0.1-SNAPSHOT>]
              |    "just-a-transitive-dependency:just-a-transitive-dependency_2.9.2:0.1-SNAPSHOT"[label=<just-a-transitive-dependency<BR/><B>just-a-transitive-dependency_2.9.2</B><BR/>0.1-SNAPSHOT>]
              |    "just-a-transitive-dependency-endpoint:just-a-transitive-dependency-endpoint_2.9.2:0.1-SNAPSHOT"[label=<just-a-transitive-dependency-endpoint<BR/><B>just-a-transitive-dependency-endpoint_2.9.2</B><BR/>0.1-SNAPSHOT>]
              |    "just-a-dependency:just-a-dependency_2.9.2:0.1-SNAPSHOT"[label=<just-a-dependency<BR/><B>just-a-dependency_2.9.2</B><BR/>0.1-SNAPSHOT>]
              |    "test-dot-file-generation:test-dot-file-generation_2.9.2:0.1-SNAPSHOT" -> "just-a-transitive-dependency:just-a-transitive-dependency_2.9.2:0.1-SNAPSHOT"
              |    "just-a-transitive-dependency:just-a-transitive-dependency_2.9.2:0.1-SNAPSHOT" -> "just-a-transitive-dependency-endpoint:just-a-transitive-dependency-endpoint_2.9.2:0.1-SNAPSHOT"
              |    "test-dot-file-generation:test-dot-file-generation_2.9.2:0.1-SNAPSHOT" -> "just-a-dependency:just-a-dependency_2.9.2:0.1-SNAPSHOT"
              |}
            """.stripMargin

          val graph : String = scala.io.Source.fromFile(dotFile.getAbsolutePath).mkString
          val errors = compareByLine(graph, expectedGraph)
          require(errors.isEmpty , errors.mkString("\n"))
          ()
        }
      )
      .dependsOn(justADependencyProject, justATransitiveDependencyProject)

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
}
