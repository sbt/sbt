scalaVersion := "2.12.8"

libraryDependencies += ("com.rengwuxian.materialedittext" % "library" % "2.1.4")
  .exclude("com.android.support", "support-v4")
  .exclude("com.android.support", "support-annotations")
  .exclude("com.android.support", "appcompat-v7")
