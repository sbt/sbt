libraryDependencies += "org.example" %% "artifacta" % "1.0.0-SNAPSHOT" withSources() classifier("tests")

externalResolvers := Seq(
	MavenCache("demo", ((baseDirectory in ThisBuild).value / "demo-repo")),
	DefaultMavenRepository
)

