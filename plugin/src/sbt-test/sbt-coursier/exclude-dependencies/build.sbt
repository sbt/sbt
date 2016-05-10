scalaVersion := "2.11.8"

coursierCachePolicies := {
  if (sys.props("os.name").startsWith("Windows"))
    coursierCachePolicies.value
  else
    Seq(coursier.CachePolicy.ForceDownload)
}

libraryDependencies += "com.github.alexarchambault" %% "argonaut-shapeless_6.1" % "1.0.0-RC1"

excludeDependencies += SbtExclusionRule("com.chuusai", "shapeless_2.11")
excludeDependencies += "io.argonaut" %% "argonaut"
