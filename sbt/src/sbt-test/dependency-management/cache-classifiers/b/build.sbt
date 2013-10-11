libraryDependencies += "org.example" %% "artifacta" % "1.0.0-SNAPSHOT" withSources() classifier("tests")

externalResolvers := Seq(
	"demo" at ( (baseDirectory in ThisBuild).value / "demo-repo").toURI.toString,
	DefaultMavenRepository
)

