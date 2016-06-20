lazy val root2 = (project in file("root2")).
  settings(
    TaskKey[Unit]("g") := {}
  )
