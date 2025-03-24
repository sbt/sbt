lazy val use = project
  .dependsOn(RootProject(file("def")) % Configurations.CompilerPlugin)
  .settings(
    scalaVersion := "2.12.20",
    autoCompilerPlugins := true
  )
