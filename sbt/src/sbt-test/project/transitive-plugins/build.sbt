ThisBuild / organization := "org.example"

lazy val root = (project in file("."))
  .settings(
    commonSettings
  )

lazy val commonSettings = Seq(
  ivyPaths := IvyPaths((baseDirectory in ThisBuild).value, Some((target in LocalRootProject).value / "ivy-cache")),
  publishTo := Some(Resolver.file("test-publish", (baseDirectory in ThisBuild).value / "repo/")),
  // to get sbt artifacts
  resolvers += {
    val ivyHome = Classpaths.bootIvyHome(appConfiguration.value) getOrElse sys.error("Launcher did not provide the Ivy home directory.")
    Resolver.file("real-local",  ivyHome / "local")(Resolver.ivyStylePatterns)
  },
  resolvers += Resolver.mavenLocal,
  resolvers += ("test-repo" at ((baseDirectory in ThisBuild).value / "repo/").asURL.toString)
)

lazy val a = (project in file("a"))
  .enablePlugins(SbtPlugin)
  .settings(
    commonSettings,
    name := "demo1",
    version := "0.1"
  )

lazy val b = (project in file("b"))
  .enablePlugins(SbtPlugin)
  .settings(
    commonSettings,
    name := "demo2",
    version := "0.2",
    addSbtPlugin("org.example" % "demo1" % "0.1")
  )

lazy val c = (project in file("c"))
  .enablePlugins(SbtPlugin)
  .settings(
    commonSettings,
    name := "demo3",
    version := "0.3",
    addSbtPlugin("org.example" % "demo2" % "0.2")
  )
