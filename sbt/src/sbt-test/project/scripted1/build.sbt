lazy val root = (project in file("."))
  .settings(
    scriptedSettings,
    sbtPlugin := true,
    resolvers += Resolver.typesafeIvyRepo("releases")
  )
