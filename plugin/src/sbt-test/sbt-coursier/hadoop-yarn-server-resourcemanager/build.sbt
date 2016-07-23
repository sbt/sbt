scalaVersion := "2.11.8"

libraryDependencies += "org.apache.hadoop" % "hadoop-yarn-server-resourcemanager" % "2.7.1"

coursierCachePolicies := {
  if (sys.props("os.name").startsWith("Windows"))
    coursierCachePolicies.value
  else
    Seq(coursier.CachePolicy.ForceDownload)
}
