lazy val root = project.in(file("."))
  .settings(
    scalaVersion := "2.12.13",
    sbtPlugin := true,
    scalacOptions ++= Seq("-Xfatal-warnings", "-Xlint")
  )
