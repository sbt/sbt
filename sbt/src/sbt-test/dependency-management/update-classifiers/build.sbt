val fooFile = taskKey[File]("sample artifact")
val RuntimeX = config("runtime")
val check = taskKey[Unit]("check")

lazy val commonSettings = Seq(
  organization := "com.example",
  version := "0.1-SNAPSHOT",
  scalaVersion := "2.11.7",
  ivyPaths := new IvyPaths( (baseDirectory in ThisBuild).value, Some((target in LocalRootProject).value / "ivy-cache")),
  fullResolvers := fullResolvers.value.filterNot(_.name == "inter-project")
)

lazy val root = (project in file(".")).
  settings(commonSettings)

lazy val classifierproducer = (project in file("classifierproducer")).
  settings(
    commonSettings,
    fooFile := { baseDirectory.value / "foo.txt" },
    addArtifact( Artifact("classifierproducer", "text", "txt", "runtime"), fooFile),
    ivyConfigurations := Seq(RuntimeX, Configurations.ScalaTool),
    publishArtifact in Compile := false,
    publishArtifact in Test := false,
    publishMavenStyle := false,
    autoScalaLibrary := false,
    crossPaths := false
  )

lazy val classifierconsumer = (project in file("classifierconsumer")).
  settings(
    commonSettings,
    libraryDependencies += "com.example" % "classifierproducer" % "0.1-SNAPSHOT" % "compile->runtime",
    check := {
      val ur = updateClassifiers.value
      val mrs = for {
        cr <- ur.configurations if cr.configuration == "compile"
        oar <- cr.details if (oar.organization == "com.example") && (oar.name == "classifierproducer")
        mr <- oar.modules if (mr.module.revision == "0.1-SNAPSHOT")
      } yield mr
      val mr = mrs.head
      if (mr.artifacts exists { case (a: Artifact, f) =>
        a.extension == "txt"
      }) ()
      else sys.error("txt artifact was not found: " + mr.toString)
    }
  )