lazy val dottyVersion = dottyLatestNightlyBuild

lazy val pluginSetting = Seq(
  name := "dividezero",
  version := "0.0.1",
  organization := "ch.epfl.lamp",
  scalaVersion := dottyVersion,

  libraryDependencies ++= Seq(
    "ch.epfl.lamp" %% "dotty" % "provided"
  )
)

lazy val plugin = (project in file("plugin")).settings(pluginSetting: _*)

lazy val app = (project in file(".")).settings(
  scalaVersion := dottyVersion,
  libraryDependencies += compilerPlugin("ch.epfl.lamp" %% "dividezero" % "0.0.1")
)
