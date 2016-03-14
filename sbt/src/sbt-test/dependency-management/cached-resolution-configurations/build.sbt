def myResolver = resolvers += Resolver.file("buggy", file("repo"))(
	Patterns(
		ivyPatterns = Seq("[organization]/[module]/[revision]/ivy.xml"),
		artifactPatterns = Seq("[organization]/[module]/[revision]/[artifact]"),
		isMavenCompatible = false,
		descriptorOptional = true,
		skipConsistencyCheck = true
	)
)

lazy val a = project settings(
	myResolver,
	updateOptions := updateOptions.value.withCachedResolution(true), //comment this line to make ws compile
	libraryDependencies += "a" % "b" % "1.0.0" % "compile->runtime",
	libraryDependencies += "a" % "b" % "1.0.0" % "compile->runtime2"
)

lazy val b = project dependsOn(a) settings(
	myResolver,
	updateOptions := updateOptions.value.withCachedResolution(true), //comment this line to make ws compile
	libraryDependencies += "a" % "b" % "1.0.1" % "compile->runtime"
)
