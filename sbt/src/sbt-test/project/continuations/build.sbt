scalaVersion := "2.8.1"

autoCompilerPlugins := true

scalacOptions += "-P:continuations:enable"

libraryDependencies ++= Seq(
  compilerPlugin("org.scala-lang.plugins" % "continuations" % "2.8.1") cross CrossVersion.Disabled,
  "junit" % "junit" % "4.7" % "test",
  "com.novocode" % "junit-interface" % "0.5" % "test"
)

initialCommands := """assert(Example.x == 20)"""
