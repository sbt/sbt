// TODO: bump to 2.12.11 once scalameta is available
ThisBuild / scalaVersion := "2.12.8"
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbIncludeInJar := true

lazy val root = (project in file("."))
