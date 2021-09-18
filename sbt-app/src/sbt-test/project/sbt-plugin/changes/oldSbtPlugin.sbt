lazy val root = project.in(file("."))
  .settings(
    scalaVersion := "2.12.15",
    sbtPlugin := true,
    scalacOptions ++= Seq("-Xfatal-warnings", "-Xlint")
  )
