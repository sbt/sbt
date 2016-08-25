lazy val foo = inputKey[Unit]("")

lazy val root = (project in file(".")).
  settings(
    name := "run-test",
    foo := {
      val x = (run in Compile).value
    }
  )
