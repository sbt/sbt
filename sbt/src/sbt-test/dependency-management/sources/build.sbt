ThisBuild / scalaVersion := "2.12.12"

lazy val root = (project in file("."))
  .settings(
    libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.5.22",
    TaskKey[Unit]("checkSources") := (updateClassifiers map checkSources).value,
    TaskKey[Unit]("checkBinaries") := (update map checkBinaries).value
  )

def getSources(report: UpdateReport)  = report.matching(artifactFilter(`classifier` = "sources") )
def checkSources(report: UpdateReport): Unit = {
  val srcs = getSources(report).map(_.getName)
  if(srcs.isEmpty)
    sys.error(s"No sources retrieved\n\n$report")
  else if (srcs.size != 9 || !srcs.exists(_ == "akka-actor_2.12-2.5.22-sources.jar")) {
// [info] [error] 	scala-java8-compat_2.12-0.8.0-sources.jar
// [info] [error] 	scala-library-2.12.12-sources.jar
// [info] [error] 	config-1.3.3-sources.jar
// [info] [error] 	akka-actor_2.12-2.5.22-sources.jar
// [info] [error] 	scala-compiler-2.12.12-sources.jar
// [info] [error] 	jline-2.14.6-sources.jar
// [info] [error] 	jansi-1.12-sources.jar
// [info] [error] 	scala-xml_2.12-1.0.6-sources.jar
// [info] [error] 	scala-reflect-2.12.12-sources.jar
    sys.error("Incorrect sources retrieved:\n\t" + srcs.mkString("\n\t"))
  } else ()
}

def checkBinaries(report: UpdateReport): Unit = {
  val srcs = getSources(report)
  if(srcs.nonEmpty) sys.error("Sources retrieved:\n\t" + srcs.mkString("\n\t"))
  else ()
}
