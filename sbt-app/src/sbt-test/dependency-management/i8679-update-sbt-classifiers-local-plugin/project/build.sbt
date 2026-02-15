lazy val meta = (project in file("."))
  .settings(
    // Just to make the test more comprehensive and check whether the additional libraries/plugins
    // are present in updateSbtClassifiers/classifiersModule.
    addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.13.1"),
    libraryDependencies += "junit" % "junit" % "4.13.2"
  )
  .dependsOn(customPlugin)

lazy val customPlugin = (project in file("custom"))
  .enablePlugins(SbtPlugin)
  .settings(
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % "2.13.0",
    )
  )