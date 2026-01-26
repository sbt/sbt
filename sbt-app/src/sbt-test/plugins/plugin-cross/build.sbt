organization := "com.example"
version      := "0.1.0-SNAPSHOT"

lazy val root = (project in file("."))
  .aggregate(plugin.projectRefs*)

lazy val plugin = (projectMatrix in file("plugin"))
  .enablePlugins(SbtPlugin)
  .settings(
    name := "example-plugin",
    scalacOptions ++= {
      if (scalaBinaryVersion.value == "2.12")
        Seq("-Xlint", "-Werror")
      else Seq("-Werror")
    },
    (pluginCrossBuild / sbtVersion) := {
      scalaBinaryVersion.value match
        case "2.12" => "1.5.8"
        case _      => "2.0.0-RC3"
    },
  )
  .jvmPlatform(scalaVersions = Seq("3.8.1", "2.12.21"))
