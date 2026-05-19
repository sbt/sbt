lazy val compilerPlugin = project
  .in(file("scala-compiler-plugin"))
  .settings(
    platform := Platform.jvm,
    crossVersion := CrossVersion.full,
  )

lazy val platformLib = project
  .in(file("platform-lib"))
  .dependsOn(compilerPlugin % "plugin")
  .settings(
    platform := "custom",
  )
