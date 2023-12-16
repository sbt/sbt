ThisBuild / useCoursier := false

lazy val commonSettings = Seq(
  autoScalaLibrary := false,
  scalaModuleInfo := None,
  (Compile / unmanagedJars) ++= {
    val converter = fileConverter.value
    val xs = scalaInstance.value.allJars.toSeq
    xs.map(_.toPath).map(x => converter.toVirtualFile(x): HashedVirtualFileRef)
  },
  (packageSrc / publishArtifact) := false,
  (packageDoc / publishArtifact) := false,
  publishMavenStyle := false
)

lazy val dep = project.
  settings(
    commonSettings,
    organization := "org.example",
    version := "1.0",
    publishTo := ((ThisBuild / baseDirectory) apply { base =>
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
      val inCache = ( (base / "target" / "use-cache") ** "*.jar").get()
      assert(inCache.isEmpty, "Cache contained jars: " + inCache)
    }).value
  )
