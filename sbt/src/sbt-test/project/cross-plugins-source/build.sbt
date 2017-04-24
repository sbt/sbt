lazy val root = (project in file("."))
  .settings(
    sbtPlugin := true,
    sbtVersion in pluginCrossBuild := "0.12.4",
    resolvers += Resolver.typesafeIvyRepo("releases")
  )
