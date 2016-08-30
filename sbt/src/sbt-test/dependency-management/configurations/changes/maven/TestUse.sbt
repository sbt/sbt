resolvers := (baseDirectory { base =>
	Resolver.file("test-repo", base / "repo") :: Nil
}).value

libraryDependencies ++= Seq(
	"org.example" %% "test" % "1.0",
	"org.example" %% "test" % "1.0" % "test",
	"org.example" %% "test" % "1.0" % "runtime"
)

name := "test-use"

organization := "test"

version := "1.0"
