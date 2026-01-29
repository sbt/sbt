lazy val root = (project in file("."))
  .enablePlugins(SbtPlugin)
  .dependsOn(lib)

lazy val lib = project in file("lib")
