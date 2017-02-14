scalaVersion := "2.11.8"

libraryDependencies += ("com.rengwuxian.materialedittext" % "library" % "2.1.4")
  .exclude("com.android.support", "support-v4")
  .exclude("com.android.support", "support-annotations")
  .exclude("com.android.support", "appcompat-v7")

coursierCachePolicies := {
  if (sys.props("os.name").startsWith("Windows"))
    coursierCachePolicies.value
  else
    Seq(coursier.CachePolicy.ForceDownload)
}
