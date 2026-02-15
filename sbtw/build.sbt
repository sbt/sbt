val sbtwScalaVersion = "3.3.7"

lazy val sbtwProj = (project in file("."))
  .enablePlugins(NativeImagePlugin)
  .settings(
    commonSettings,
    name := "sbtw",
    description := "Windows drop-in launcher for sbt (replaces sbt.bat)",
    scalaVersion := sbtwScalaVersion,
    crossPaths := false,
    Compile / mainClass := Some("sbtw.Main"),
    libraryDependencies += "com.github.scopt" %% "scopt" % "4.1.0",
    nativeImageVersion := "23.0",
    nativeImageJvm := "graalvm-java23",
    nativeImageOutput := (target.value / "bin" / "sbtw").toPath.toFile,
    nativeImageOptions ++= Seq(
      "--no-fallback",
      s"--initialize-at-run-time=sbtw",
      "-H:+ReportExceptionStackTraces",
      s"-H:Name=${(target.value / "bin" / "sbtw").getAbsolutePath}",
    ),
    Utils.noPublish,
  )
