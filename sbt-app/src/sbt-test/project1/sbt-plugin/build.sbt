lazy val root = project.in(file("."))
  .enablePlugins(SbtPlugin)
  .settings(
    scalaVersion := "2.12.20",
    scalacOptions ++= Seq("-Xfatal-warnings", "-Xlint")
  )
