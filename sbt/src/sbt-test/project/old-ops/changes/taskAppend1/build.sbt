lazy val foo = taskKey[String]("")
lazy val root = (project in file(".")).
  settings(
    scalacOptions <+= foo,
    foo := "-unchecked"
  )
