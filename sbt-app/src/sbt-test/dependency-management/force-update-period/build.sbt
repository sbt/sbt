ThisBuild / useCoursier := false

name := "force-update-period"
scalaVersion := "2.12.20"
libraryDependencies += "log4j" % "log4j" % "1.2.16" % "compile"
autoScalaLibrary := false

TaskKey[Unit]("check-last-update-time") := {
  val s = streams.value
  val updateOutput = crossTarget.value / "update" / updateCacheName.value / "output"
  if (!updateOutput.exists()) {
    sys.error("Update cache does not exist")
  }
  val timeDiff = System.currentTimeMillis() - updateOutput.lastModified()
  s.log.info(s"Amount of time since last full update: $timeDiff")
  if (timeDiff > 5000) {
    sys.error("Update not performed")
  }
}
