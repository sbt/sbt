ThisBuild / scalaVersion := "2.11.12"

lazy val a = project
  .settings(
    libraryDependencies += "com.chuusai" %% "shapeless" % "2.3.234" from "https://oss.sonatype.org/content/repositories/releases/com/chuusai/shapeless_2.11/2.3.1/shapeless_2.11-2.3.1.jar"
  )

lazy val b = project
  .dependsOn(a)

lazy val root = project
  .in(file("."))
  .aggregate(a, b)
