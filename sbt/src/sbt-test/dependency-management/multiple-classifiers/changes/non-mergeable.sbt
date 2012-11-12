libraryDependencies ++= Seq(
	"org.easytesting" % "fest-assert"  % "1.4",
	"org.easytesting" % "fest-assert"  % "1.4" % "test" intransitive())

autoScalaLibrary := false

TaskKey[Unit]("check") <<= (externalDependencyClasspath in Compile, externalDependencyClasspath in Test) map { (cp, tcp) =>
        assert(cp.size == 2, "Expected 2 jars on compile classpath, got: " + cp.files.mkString("(", ", ", ")"))
	// this should really be 1 because of intransitive(), but Ivy doesn't handle this.
	// So, this test can only check that the assertion reported in #582 isn't triggered.
        assert(tcp.size == 2, "Expected 2 jar on test classpath, got: " + tcp.files.mkString("(", ", ", ")"))
}

