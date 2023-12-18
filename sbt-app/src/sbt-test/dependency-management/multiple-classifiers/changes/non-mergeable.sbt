libraryDependencies ++= Seq(
  "org.easytesting" % "fest-assert"  % "1.4",
  ("org.easytesting" % "fest-assert"  % "1.4" % Test).intransitive(),
)

autoScalaLibrary := false

TaskKey[Unit]("check") := {
  val cp = (Compile / externalDependencyClasspath).value
  val tcp = (Test / externalDependencyClasspath).value
        assert(cp.size == 2, "Expected 2 jars on compile classpath, got: " + cp.mkString("(", ", ", ")"))
  // this should really be 1 because of intransitive(), but Ivy doesn't handle this.
  // So, this test can only check that the assertion reported in #582 isn't triggered.
        assert(tcp.size == 2, "Expected 2 jar on test classpath, got: " + tcp.mkString("(", ", ", ")"))
}
