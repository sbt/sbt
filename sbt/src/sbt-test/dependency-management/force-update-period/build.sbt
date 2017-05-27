libraryDependencies += "log4j" % "log4j" % "1.2.16" % "compile"

autoScalaLibrary := false

crossPaths := false

TaskKey[Unit]("check-last-update-time") := (streams map { (s) =>
  val fullUpdateOutput = s.cacheDirectory / "out"
  val timeDiff = System.currentTimeMillis()-fullUpdateOutput.lastModified()
  val exists = fullUpdateOutput.exists()
  s.log.info(s"Amount of time since last full update: $timeDiff")
  if (exists && timeDiff > 5000) {
    sys.error("Full update not performed")
  }
}).value
