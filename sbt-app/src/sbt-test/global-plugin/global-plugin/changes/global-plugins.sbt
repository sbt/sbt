// use a small java library instead of a plugin to avoid incompatibilities when upgrading
// This version should be overridden by the one in the project.
libraryDependencies += "junit" % "junit" % "4.5"

addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.13.1")
