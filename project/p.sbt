libraryDependencies += "net.databinder" %% "dispatch-http" % "0.8.8"

addSbtPlugin("com.typesafe.sbt" % "sbt-site" % "0.6.0")

resolvers += "jgit-repo" at "http://download.eclipse.org/jgit/maven"

addSbtPlugin("com.typesafe.sbt" % "sbt-ghpages" % "0.5.0")