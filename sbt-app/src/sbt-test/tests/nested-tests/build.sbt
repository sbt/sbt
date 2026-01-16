val scalacheck = "org.scalacheck" %% "scalacheck" % "1.14.0"

ThisBuild / scalaVersion := "2.12.21"
ThisBuild / version := "0.0.1" 
ThisBuild / organization := "org.catastrophe"

libraryDependencies += scalacheck % Test
name := "broken"
