// Regression test for sbt/sbt#8731: scalaOrganization must thread into the
// compiler-bridge ModuleID instead of being hardcoded to org.scala-lang.
// See sbt/sbt#9259 for why this is a settings-graph assertion rather than a
// real cross-org compile.

ThisBuild / scalaOrganization := "io.example.testorg"
ThisBuild / scalaVersion := "3.7.4"

lazy val checkScala3 =
  taskKey[Unit]("scalaOrganization threads into scala3-sbt-bridge for Scala 3")
lazy val checkScala2 =
  taskKey[Unit]("scalaOrganization threads into scala2-sbt-bridge for Scala 2.13.12+")

lazy val root = (project in file("."))
  .settings(
    checkScala3 := {
      val m = scalaCompilerBridgeSource.value
      assert(
        m.organization == "io.example.testorg",
        s"expected organization io.example.testorg, got $m",
      )
      assert(
        m.name == "scala3-sbt-bridge",
        s"expected name scala3-sbt-bridge, got $m",
      )
    }
  )

lazy val s213 = (project in file("s213"))
  .settings(
    scalaVersion := "2.13.12",
    checkScala2 := {
      val m = scalaCompilerBridgeSource.value
      assert(
        m.organization == "io.example.testorg",
        s"expected organization io.example.testorg, got $m",
      )
      assert(
        m.name == "scala2-sbt-bridge",
        s"expected name scala2-sbt-bridge, got $m",
      )
    },
  )
