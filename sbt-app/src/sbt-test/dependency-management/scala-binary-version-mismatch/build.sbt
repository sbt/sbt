lazy val a = project.settings(scalaVersion := "2.13.18")
lazy val b = project.settings(scalaVersion := "2.12.21").dependsOn(a)
