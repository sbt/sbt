val scalatest = "org.scalatest" %% "scalatest" % "3.2.2"
val munit = "org.scalameta" %% "munit" % "0.7.22"

ThisBuild / scalaVersion := "2.12.20"

libraryDependencies += scalatest % Test
libraryDependencies += munit % Test

testFrameworks += new TestFramework("munit.Framework")
