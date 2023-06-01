lazy val root = project.in(file("."))
  .settings(
    scalaVersion := "2.12.18",
    sbtPlugin := true,
    scalacOptions ++= Seq("-Xfatal-warnings", "-Xlint")
  )
