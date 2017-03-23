val paradiseVersion = "2.0.1"
val commonSettings = Seq(
  version := "1.0.0",
  scalacOptions ++= Seq(""),
  scalaVersion := "2.11.4",
  resolvers += Resolver.sonatypeRepo("snapshots"),
  resolvers += Resolver.sonatypeRepo("releases"),
  addCompilerPlugin("org.scalamacros" % "paradise" % paradiseVersion cross CrossVersion.full)
)

lazy val root = (project in file(".")).
  aggregate(macros, core).
  settings(
    commonSettings,
    run := (run in Compile in core).evaluated
  )

lazy val macros = (project in file("macros")).
  settings(
    commonSettings,
    libraryDependencies += (scalaVersion)("org.scala-lang" % "scala-reflect" % _).value,
    libraryDependencies ++= (
      if (scalaVersion.value.startsWith("2.10")) List("org.scalamacros" %% "quasiquotes" % paradiseVersion)
      else Nil
    )
  )

lazy val core = (project in file("core")).
  dependsOn(macros).
  settings(
    commonSettings
  )
