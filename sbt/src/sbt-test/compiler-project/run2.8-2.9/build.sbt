lazy val specs = Def.setting {
  "org.scala-tools.testing" %% "specs" % (scalaVersion.value match {
    case "2.8.1" | "2.8.2" | "2.9.0" => "1.6.8"
    case "2.9.3" => "1.6.9"
  })
}

lazy val root = (project in file(".")).
  settings(
    scalaVersion := "2.8.1",
    libraryDependencies += specs.value % Test,
    libraryDependencies <+= scalaVersion("org.scala-lang" % "scala-compiler" % _)
  )
