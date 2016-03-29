lazy val root = (project in file(".")).
  configs(IntegrationTest).
  settings(
    Defaults.itSettings,
    libraryDependencies += specs
  )

lazy val specs = "org.specs2" % "specs2_2.10" % "1.12.3" % IntegrationTest
