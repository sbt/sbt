ThisBuild / organization := "com.eed3si9n"
ThisBuild / dynverSonatypeSnapshots := true
ThisBuild / version := {
  val orig = (ThisBuild / version).value
  if (orig.endsWith("-SNAPSHOT")) "0.7.0-SNAPSHOT"
  else orig
}
ThisBuild / description  := "sbt plugin to define project matrix for cross building"
ThisBuild / homepage     := Some(url("https://github.com/sbt/sbt-projectmatrix"))
ThisBuild / licenses     := Seq("MIT License" -> url("https://github.com/sbt/sbt-projectmatrix/blob/master/LICENSE"))

lazy val root = (project in file("."))
  .enablePlugins(SbtPlugin)
  .settings(
    name := "sbt-projectmatrix",
    pluginCrossBuild / sbtVersion := "1.2.8",
    scalacOptions := Seq("-deprecation", "-unchecked"),
    publishMavenStyle := false,
    bintrayRepository := "sbt-plugins",
    publishTo := (bintray / publishTo).value,
    scriptedLaunchOpts := { scriptedLaunchOpts.value ++
      Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
    },
    scriptedBufferLog := false,
  )
