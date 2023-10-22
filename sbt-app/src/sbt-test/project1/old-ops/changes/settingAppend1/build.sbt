lazy val root = (project in file(".")).
  settings(
    crossScalaVersions <+= scalaVersion
  )
