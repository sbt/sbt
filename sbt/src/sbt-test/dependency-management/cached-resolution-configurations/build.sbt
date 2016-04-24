def commonSettings: Seq[Def.Setting[_]] =
  Seq(
    organization := "com.example",
    ivyPaths := new IvyPaths( (baseDirectory in ThisBuild).value, Some((baseDirectory in LocalRootProject).value / "ivy-cache")),
    dependencyCacheDirectory := (baseDirectory in LocalRootProject).value / "dependency",
    resolvers += Resolver.file("buggy", (baseDirectory in LocalRootProject).value / "repo")(
      Patterns(
        ivyPatterns = Seq("[organization]/[module]/[revision]/ivy.xml"),
        artifactPatterns = Seq("[organization]/[module]/[revision]/[artifact]"),
        isMavenCompatible = false,
        descriptorOptional = true,
        skipConsistencyCheck = true
      )
    )
  )

lazy val a = project settings(
  commonSettings,
  updateOptions := updateOptions.value.withCachedResolution(true), //comment this line to make ws compile
  libraryDependencies += "a" % "b" % "1.0.0" % "compile->runtime",
  libraryDependencies += "a" % "b" % "1.0.0" % "compile->runtime2"
)

lazy val b = project dependsOn(a) settings(
  commonSettings,
  updateOptions := updateOptions.value.withCachedResolution(true), //comment this line to make ws compile
  libraryDependencies += "a" % "b" % "1.0.1" % "compile->runtime"
)
