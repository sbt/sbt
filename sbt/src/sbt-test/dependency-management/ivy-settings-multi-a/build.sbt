lazy val commonSettings = Seq(
  autoScalaLibrary := false,
  unmanagedJars in Compile ++= (scalaInstance map (_.allJars.toSeq)).value
)

lazy val dep = project.
  settings(
    commonSettings,
    organization := "org.example",
    version := "1.0"
  )

lazy val use = project.
  dependsOn(dep).
  settings(
    commonSettings,
    libraryDependencies += "junit" % "junit" % "4.5",
    externalIvySettings()
  )
