scalaVersion := "3.8.4"

@transient
lazy val check = taskKey[Unit]("")

lazy val root = rootProject
  .autoAggregate

lazy val app = project
  .settings(
    check := {
      val b = (ThisBuild / baseDirectory).value
      val fo = (Compile / run / forkOptions).value
      assert(fo.workingDirectory == Some(b), s"${fo.workingDirectory}")
    },
    Compile / run / fork := true,
  )
