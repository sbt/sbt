ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always

import scala.concurrent.duration._

libraryDependencies += "org.scala-sbt" % "sbt" % "1.3.0"

ThisBuild / checkBuildSources / pollInterval := 0.seconds
