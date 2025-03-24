lazy val root = project.in(file("."))
  .settings(
    scalaVersion := "2.12.20",
    sbtPlugin := true,
    scalacOptions ++= Seq("-Xfatal-warnings", "-Xlint")
  )
