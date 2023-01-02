lazy val root = project.in(file("."))
  .enablePlugins(SbtPlugin)
  .settings(
    scalaVersion := "2.12.17",
    scalacOptions ++= Seq("-Xfatal-warnings", "-Xlint")
  )
