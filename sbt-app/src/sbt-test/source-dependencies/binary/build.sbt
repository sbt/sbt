ThisBuild / scalaVersion := "2.12.12"

lazy val dep = project

lazy val use = project.
  settings(
    (Compile / unmanagedJars) += ((dep / Compile / packageBin) map Attributed.blank).value
  )
