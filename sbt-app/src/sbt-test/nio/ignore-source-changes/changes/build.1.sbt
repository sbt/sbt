import scala.concurrent.duration.DurationInt

ThisBuild / checkBuildSources / pollInterval := 0.seconds

lazy val sub = project.in(file("sub"))

// change 1
