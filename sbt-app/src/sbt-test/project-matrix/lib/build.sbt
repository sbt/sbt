version := "0.1.0-SNAPSHOT"

lazy val util = projectMatrix
  .jvmPlatform(scalaVersions = Seq("2.12.20", "2.13.13"))

lazy val root = (projectMatrix in file("."))
  .dependsOn(util)
  .jvmPlatform(scalaVersions = Seq("2.12.20"))

// ss is second system
lazy val ss = projectMatrix
  .dependsOn(util)
  .jvmPlatform(scalaVersions = Seq("2.13.13"))

lazy val strayJar = project
  .settings(scalaVersion := "2.12.20")
