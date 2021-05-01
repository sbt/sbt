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
    scriptedLaunchOpts := { scriptedLaunchOpts.value ++
      Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
    },
    scriptedBufferLog := false,
  )

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/sbt/sbt-projectmatrix"),
    "scm:git@github.com:sbt/sbt-projectmatrix.git"
  )
)
ThisBuild / developers := List(
  Developer(
    id    = "eed3si9n",
    name  = "Eugene Yokota",
    email = "@eed3si9n",
    url   = url("https://eed3si9n.com/")
  )
)
ThisBuild / pomIncludeRepository := { _ => false }
ThisBuild / publishTo := {
  val nexus = "https://oss.sonatype.org/"
  val v = (ThisBuild / version).value
  if (v.endsWith("-SNAPSHOT")) Some("snapshots" at nexus + "content/repositories/snapshots")
  else Some("releases" at nexus + "service/local/staging/deploy/maven2")
}
ThisBuild / publishMavenStyle := true
