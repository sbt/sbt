scalaVersion := "2.11.8"

libraryDependencies += "org.apache.zookeeper" % "zookeeper" % "3.5.0-alpha"

coursierCachePolicies := {
  if (sys.props("os.name").startsWith("Windows"))
    coursierCachePolicies.value
  else
    Seq(coursier.CachePolicy.ForceDownload)
}
