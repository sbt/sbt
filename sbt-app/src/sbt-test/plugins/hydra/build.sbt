ThisBuild / scalaVersion := "2.12.20"

lazy val check = taskKey[Unit]("")

lazy val root = (project in file("."))
  .settings(
    name := "hello",
    check := {
      val rs = scalaCompilerBridgeResolvers.value
      assert(rs.exists(r => r.name == "Triplequote Maven Releases"))
    }
  )
