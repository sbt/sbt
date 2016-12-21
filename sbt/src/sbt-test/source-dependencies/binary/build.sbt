lazy val dep = project.
  settings(
    scalaVersion := "2.11.8"
  )

lazy val use = project.
  settings(
    scalaVersion := "2.11.8",
    unmanagedJars in Compile += (packageBin in (dep, Compile) map Attributed.blank).value
  )
