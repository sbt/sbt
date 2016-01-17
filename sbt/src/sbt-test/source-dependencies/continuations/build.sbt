autoCompilerPlugins := true

libraryDependencies += compilerPlugin("org.scala-lang.plugins" % "continuations" % scalaVersion.value)

scalacOptions += "-P:continuations:enable"
