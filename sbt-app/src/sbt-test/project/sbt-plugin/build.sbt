lazy val root = project.in(file("."))
  .enablePlugins(SbtPlugin)
  .settings(
    scalaVersion := "2.12.14",
    scalacOptions ++= Seq("-Xfatal-warnings", "-Xlint")
  )
