scalaOrganization := "org.typelevel"
scalaVersion := "2.11.7"
scalacOptions += "-Xexperimental"

coursierCachePolicies := {
  if (sys.props("os.name").startsWith("Windows"))
    coursierCachePolicies.value
  else
    Seq(coursier.CachePolicy.ForceDownload)
}
