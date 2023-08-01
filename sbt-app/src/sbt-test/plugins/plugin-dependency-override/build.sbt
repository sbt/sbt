lazy val plugin = (project in file("baseplugin"))
  .settings(
    version := "0.1.0",
    name:= "base-plugin",
    organization := "com.example"
  )
  .enablePlugins(SbtPlugin)

lazy val plugin1 = (project in file("plugin1"))
  .settings(
    version := "1.0.0",
    name := "test-plugin",
    organization := "com.example"
  )
  .enablePlugins(SbtPlugin)
  .dependsOn(plugin)

lazy val plugin2 = (project in file("plugin2"))
  .settings(
    name := "test-plugin",
    organization := "com.example",
    version := "2.0.0"
  )
  .dependsOn(plugin)
  .enablePlugins(SbtPlugin)

lazy val dependentplugin = (project in file("dependentplugin"))
  .settings(
    name := "dependent-plugin",
    organization := "com.example",
    version := "0.2.0"
  )
  .dependsOn(plugin2)
  .enablePlugins(SbtPlugin)
