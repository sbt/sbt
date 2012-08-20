autoScalaLibrary := false

ivyPaths <<= (baseDirectory, target)( (dir, t) => new IvyPaths(dir, Some(t / "ivy-cache")))

libraryDependencies ++= Seq(
	"org.sat4j" % "org.sat4j.pb" % "2.3.1",
	"org.sat4j" % "org.sat4j.core" % "2.3.1"
)

TaskKey[Unit]("check-update") <<= update map { report =>
	val mods = report.configuration(Compile.name).get.allModules.map(_.name).toSet
	val expected = Set("org.sat4j.pb", "org.sat4j.core")
	if(mods != expected)
		error("Expected modules " + expected  + ", got: " + mods)
}

TaskKey[Unit]("check-classpath") <<= dependencyClasspath in Compile map { cp =>
	val jars = cp.files.map(_.getName).toSet
	val expected = Set("org.sat4j.pb-2.3.1.jar", "org.sat4j.core-2.3.1.jar")
	if(jars != expected)
		error("Expected jars " + expected  + ", got: " + jars)
}