lazy val root = project.in(file("."))
  .settings(
    scalaVersion := "2.12.14",
    sbtPlugin := true,
    scalacOptions ++= Seq("-Xfatal-warnings", "-Xlint")
  )
