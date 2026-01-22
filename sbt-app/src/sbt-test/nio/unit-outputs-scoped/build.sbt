import sbt.internal.FileChangesMacro.*

val a = taskKey[Seq[java.nio.file.Path]]("")
val b = taskKey[Unit]("")
val scope = taskKey[Unit]("")

lazy val root = (project in file("."))
  .settings(
    name := "unit-outputs-scoped-test",
    (scope / a) := {
      Seq.empty
    },
    b := {
      // This should work after the fix - accessing outputFileChanges on a scoped task
      val changes = (scope / a).outputFileChanges
      println(s"Scoped output file changes: $changes")
    }
  )