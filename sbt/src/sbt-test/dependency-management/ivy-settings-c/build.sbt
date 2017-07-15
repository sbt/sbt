lazy val commonSettings = Seq(
  autoScalaLibrary := false,
  scalaModuleInfo := None,
  unmanagedJars in Compile ++= (scalaInstance map (_.allJars.toSeq)).value,
  publishArtifact in packageSrc := false,
  publishArtifact in packageDoc := false,
  publishMavenStyle := false
)

lazy val dep = project.
  settings(
    commonSettings,
    organization := "org.example",
    version := "1.0",
    publishTo := (baseDirectory in ThisBuild apply { base =>
      Some(Resolver.file("file", base / "repo")(Resolver.ivyStylePatterns))
    }).value
  )

lazy val use = project.
  settings(
    commonSettings,
    libraryDependencies += "org.example" %% "dep" % "1.0",
    externalIvySettings(),
    publishTo := (baseDirectory { base =>
      Some(Resolver.file("file", base / "repo")(Resolver.ivyStylePatterns))
    }).value,
    TaskKey[Unit]("check") := (baseDirectory map {base =>
      val inCache = ( (base / "target" / "use-cache") ** "*.jar").get
      assert(inCache.isEmpty, "Cache contained jars: " + inCache)
    }).value
  )
