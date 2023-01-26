ThisBuild / scalaVersion := "3.0.0"

lazy val check = taskKey[Unit]("check")

lazy val root = project.in(file("."))
  .settings(
    check := {
      val dirs = (Compile / unmanagedSourceDirectories).value
      assert(dirs == dirs.distinct)
    }
  )
