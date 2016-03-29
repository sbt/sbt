import sbt.internal.librarymanagement.syntax._

lazy val root = (project in file(".")).
  settings(
    libraryDependencies += "net.liftweb" % "lift-webkit" % "1.0" intransitive(),
    libraryDependencies += "org.scalacheck" % "scalacheck" % "1.5" intransitive(),
    autoScalaLibrary := false,
    managedScalaInstance := false,
    transitiveClassifiers := Seq("sources"),
    TaskKey[Unit]("check-sources") <<= updateClassifiers map checkSources,
    TaskKey[Unit]("check-binaries") <<= update map checkBinaries
  )

def getSources(report: UpdateReport)  = report.matching(artifactFilter(`classifier` = "sources") )
def checkSources(report: UpdateReport): Unit =
{
  val srcs = getSources(report)
  if(srcs.isEmpty)
    sys.error("No sources retrieved")
  else if(srcs.size != 2)
    sys.error("Incorrect sources retrieved:\n\t" + srcs.mkString("\n\t"))
  else
    ()
}

def checkBinaries(report: UpdateReport): Unit =
  {
    val srcs = getSources(report)
    if(srcs.nonEmpty) sys.error("Sources retrieved:\n\t" + srcs.mkString("\n\t"))
    else ()
  }
