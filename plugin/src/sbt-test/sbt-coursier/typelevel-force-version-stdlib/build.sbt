scalaOrganization := "org.typelevel"
scalaVersion := "2.11.7"
scalacOptions += "-Xexperimental"

// no effect, as the right version is forced anyway (to scalaVersion.value)
libraryDependencies += "org.typelevel" % "scala-library" % "2.11.12345"

coursierCachePolicies := {
  if (sys.props("os.name").startsWith("Windows"))
    coursierCachePolicies.value
  else
    Seq(coursier.CachePolicy.ForceDownload)
}
