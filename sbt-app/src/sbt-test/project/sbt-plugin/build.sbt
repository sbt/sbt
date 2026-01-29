lazy val root = project.in(file("."))
  .enablePlugins(SbtPlugin)
  .settings(
    scalaVersion := "2.12.21",
    scalacOptions ++= Seq("-Werror", "-Xlint")
  )
