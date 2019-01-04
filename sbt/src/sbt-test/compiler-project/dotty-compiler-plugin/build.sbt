lazy val dottyVersion = "0.11.0-RC1"

ThisBuild / scalaVersion := dottyVersion
ThisBuild / organization := "com.example"

lazy val plugin = (project in file("plugin"))
  .settings(
    name := "dividezero",
    version := "0.0.1"
  )

lazy val app = (project in file("."))
  .settings(
    libraryDependencies += compilerPlugin("com.example" %% "dividezero" % "0.0.1"),
  )
