Global / localCacheDirectory := baseDirectory.value / "diskcache"

// https://github.com/sbt/sbt/issues/8225
exportJars := false
libraryDependencies += "org.scalameta" %% "munit" % "1.0.0" % Test
