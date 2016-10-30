scalaVersion := "2.11.8"

coursierCachePolicies := {
  if (sys.props("os.name").startsWith("Windows"))
    coursierCachePolicies.value
  else
    Seq(coursier.CachePolicy.ForceDownload)
}

libraryDependencies += "ccl.northwestern.edu" % "netlogo" % "5.3.1" % "provided" from s"https://github.com/NetLogo/NetLogo/releases/download/5.3.1/NetLogo.jar"
