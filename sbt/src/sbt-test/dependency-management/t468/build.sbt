autoScalaLibrary := false

ivyPaths := IvyPaths(baseDirectory.value, Some(target.value / "ivy-cache"))

libraryDependencies ++= Seq(
	"org.sat4j" % "org.sat4j.pb" % "2.3.1",
	"org.sat4j" % "org.sat4j.core" % "2.3.1"
)

TaskKey[Unit]("checkUpdate") := {
	val report = update.value
	val mods = report.configuration(Compile.name).get.allModules.map(_.name).toSet
	val expected = Set("org.sat4j.pb", "org.sat4j.core")
	if(mods != expected)
		sys.error("Expected modules " + expected  + ", got: " + mods)
}

TaskKey[Unit]("checkClasspath") := (dependencyClasspath in Compile map { cp =>
	val jars = cp.files.map(_.getName).toSet
	// Note: pb depends on tests artifact in core for no good reason.  Previously this was not correctly added to the classpath.
	val expected = Set("org.sat4j.pb-2.3.1.jar", "org.sat4j.core-2.3.1.jar", "org.sat4j.core-2.3.1-tests.jar")
	if(jars != expected)
		sys.error("Expected jars " + expected  + ", got: " + jars)
}).value
