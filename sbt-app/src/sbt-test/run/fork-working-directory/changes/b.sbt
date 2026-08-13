scalaVersion := "3.8.4"

@transient
lazy val check = taskKey[Unit]("")

lazy val root = rootProject
  .autoAggregate

lazy val app = project
  .settings(
    check := {
      val b = baseDirectory.value
      val fo = (Compile / run / forkOptions).value
      assert(fo.workingDirectory == Some(b), s"${fo.workingDirectory}")
    },
    Compile / run / fork := true,
    // app's own baseDirectory is explicitly requested as run's working
    // directory, so `app/run` is expected to execute from app/.
    Compile / run / baseDirectory := baseDirectory.value,
  )
