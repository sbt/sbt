import sbt.internal.FileChangesMacro.*

val a = taskKey[Unit]("")
val b = taskKey[Unit]("")

lazy val root = (project in file("."))
  .settings(
    name := "unit-outputs-test",
    a / fileOutputs += (baseDirectory.value / "a-output.txt").toGlob,
    a := {
      ()
    },
    b := {
      // This should work after the fix - accessing outputFileChanges on a Unit-returning task
      val changes = a.outputFileChanges
      println(s"Output file changes: $changes")
    }
  )