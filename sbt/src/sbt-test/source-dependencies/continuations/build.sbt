scalaVersion := "2.10.6"
autoCompilerPlugins := true
libraryDependencies +=
  compilerPlugin("org.scala-lang.plugins" % "continuations" % scalaVersion.value) cross CrossVersion.Disabled
scalacOptions += "-P:continuations:enable"
