lazy val root = project.in(file("."))
  .settings(
    scalaVersion := "2.12.21",
    sbtPlugin := true,
    scalacOptions ++= Seq("-Werror", "-Xlint")
  )
