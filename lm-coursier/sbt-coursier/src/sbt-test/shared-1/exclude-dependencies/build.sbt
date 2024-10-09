
lazy val a = project
  .settings(
    organization := "io.get-coursier.test",
    name := "sbt-coursier-exclude-dependencies",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := "2.12.8",
    libraryDependencies += "com.github.alexarchambault" %% "argonaut-shapeless_6.2" % "1.2.0-M11",
    excludeDependencies += sbt.ExclusionRule("com.chuusai", "shapeless_2.12"),
    excludeDependencies += "io.argonaut" %% "argonaut"
  )

lazy val b = project
  .settings(
    organization := "io.get-coursier.test",
    name := "sbt-coursier-exclude-dependencies-2",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := "2.12.8",
    libraryDependencies ++= Seq(
      "com.github.alexarchambault" %% "argonaut-shapeless_6.2" % "1.2.0-M11",
      "com.chuusai" %% "shapeless" % "2.3.3",
      "io.argonaut" %% "argonaut" % "6.2.3"
    ),
    excludeDependencies += sbt.ExclusionRule("com.chuusai", "shapeless_2.12"),
    excludeDependencies += "io.argonaut" %% "argonaut"
  )
