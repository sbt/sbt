lazy val a = project.settings(common: _*).settings(
	// verifies that a can be published as an ivy.xml file and preserve the extra artifact information,
	//   such as a classifier
	libraryDependencies := Seq("net.sf.json-lib" % "json-lib" % "2.4" classifier "jdk15" intransitive()),
	// verifies that an artifact without an explicit configuration gets published in all public configurations
	artifact in (Compile,packageBin) := Artifact("demo")
)

lazy val b = project.settings(common: _*).settings(
	libraryDependencies := Seq(organization.value %% "a" % version.value)
)

organization in ThisBuild := "org.example"

version in ThisBuild := "1.0"

lazy val common = Seq(
	autoScalaLibrary := false, // avoid downloading fresh scala-library/scala-compiler
	managedScalaInstance := false,
	ivyPaths := IvyPaths( (baseDirectory in ThisBuild).value, Some((target in LocalRootProject).value / "ivy-cache"))
)
