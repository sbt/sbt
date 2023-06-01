lazy val root = project.in(file("."))
  .enablePlugins(SbtPlugin)
  .settings(
    scalaVersion := "2.12.18",
    scalacOptions ++= Seq("-Xfatal-warnings", "-Xlint")
  )
