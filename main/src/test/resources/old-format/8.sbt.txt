name := "apidoc"

scalaVersion in ThisBuild := "2.11.1"

lazy val core = project
  .in(file("core"))
  .settings(commonSettings: _*)
  .settings(
    // play-json needs this to resolve correctly when not using Gilt's internal mirrors
    resolvers += "Typesafe Maven Repository" at "http://repo.typesafe.com/typesafe/maven-releases/",
    version := "1.0-SNAPSHOT",
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-json" % "2.3.0"
    )
  )

lazy val api = project
  .in(file("api"))
  .dependsOn(core)
  .aggregate(core)
  .enablePlugins(PlayScala)
  .settings(commonSettings: _*)
  .settings(
    version := "1.0-SNAPSHOT",
    libraryDependencies ++= Seq(
      jdbc,
      anorm,
      "org.postgresql" % "postgresql" % "9.3-1101-jdbc4",
      "org.mindrot"          %  "jbcrypt"                 % "0.3m"
    )
  )

lazy val www = project
  .in(file("www"))
  .dependsOn(core)
  .aggregate(core)
  .enablePlugins(PlayScala)
  .settings(commonSettings: _*)
  .settings(
    version := "1.0-SNAPSHOT",
    libraryDependencies ++= Seq(
      ws
    )
  )

lazy val commonSettings: Seq[Setting[_]] = Seq(
  name <<= name("apidoc-" + _),
  libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % "2.2.0" % "test"
  ),
  scalacOptions += "-feature"
) ++ instrumentSettings ++ Seq(ScoverageKeys.highlighting := true)



