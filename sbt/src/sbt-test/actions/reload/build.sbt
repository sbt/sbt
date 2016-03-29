lazy val root = (project in file(".")).
  aggregate(sub).
  settings(
    TaskKey[Unit]("f") := sys.error("f")
  )

lazy val sub = (project in file("sub")).
  settings(
    TaskKey[Unit]("f") := {}
  )
