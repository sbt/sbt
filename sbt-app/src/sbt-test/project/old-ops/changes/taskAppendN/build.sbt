lazy val foo = taskKey[Seq[String]]("")
lazy val root = (project in file(".")).
  settings(
    scalacOptions <++= foo,
    foo := Seq("-unchecked")
  )
