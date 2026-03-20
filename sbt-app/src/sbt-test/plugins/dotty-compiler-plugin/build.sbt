ThisBuild / scalaVersion := "3.3.7"

lazy val plugin = project
  .in(file("plugin"))
  .settings(
    name := "dividezero",
    version := "0.0.1",
    organization := "ch.epfl.lamp",

    scalacOptions ++= Seq(
      "-language:implicitConversions"
    ),

    libraryDependencies ++= Seq(
      "org.scala-lang" %% "scala3-compiler" % scalaVersion.value % "provided"
    )
  )

lazy val app = project
  .in(file("app"))

lazy val appOk = project
  .in(file("appOk"))
