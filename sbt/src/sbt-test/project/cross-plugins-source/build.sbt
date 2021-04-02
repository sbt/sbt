lazy val root = (project in file("."))
  .settings(
    sbtPlugin := true,
    sbtVersion in pluginCrossBuild := "0.13.15",
    resolvers += Resolver.typesafeIvyRepo("releases")
  )
