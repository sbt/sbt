
scalaVersion := "2.12.8"

organization := "io.get-coursier.test"
name := "sbt-coursier-exclude-dependencies"
version := "0.1.0-SNAPSHOT"

libraryDependencies += "com.github.alexarchambault" %% "argonaut-shapeless_6.2" % "1.2.0-M11"

excludeDependencies += sbt.ExclusionRule("com.chuusai", "shapeless_2.12")
excludeDependencies += "io.argonaut" %% "argonaut"
