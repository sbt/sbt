publishMavenStyle := false

resolvers := (baseDirectory { base =>
	Resolver.file("test-repo", base / "repo")(Patterns(false, Resolver.mavenStyleBasePattern)) :: Nil
}).value

libraryDependencies ++= Seq(
	"org.example" %% "test-ivy" % "1.0",
	"org.example" %% "test-ivy" % "1.0" % "test",
	"org.example" %% "test-ivy" % "1.0" % "runtime"
)

name := "test-ivy-use"

organization := "test"

version := "1.0"
