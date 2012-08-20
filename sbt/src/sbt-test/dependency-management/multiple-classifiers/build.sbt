libraryDependencies ++= Seq("natives-windows", "natives-linux", "natives-osx") map ( c =>
	"org.lwjgl.lwjgl" % "lwjgl-platform" % "2.8.2" classifier c
)

autoScalaLibrary := false

TaskKey[Unit]("check") <<= dependencyClasspath in Compile map { cp =>
	assert(cp.size == 3, "Expected 3 jars, got: " + cp.files.mkString("(", ", ", ")"))
}

TaskKey[Unit]("check-pom") <<= makePom map { file =>
	val pom = xml.XML.loadFile(file)
	val actual = pom \\ "dependencies"
	def depSection(classifier: String) =
		<dependency>
			<groupId>org.lwjgl.lwjgl</groupId>
			<artifactId>lwjgl-platform</artifactId>
			<version>2.8.2</version>
			<classifier>{classifier}</classifier>
		</dependency>
	val sections = depSection("natives-windows") ++ depSection("natives-linux") ++ depSection("natives-osx")
	val expected = <dependencies xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns="http://maven.apache.org/POM/4.0.0" >
		{sections}
	</dependencies>
	val pp = new xml.PrettyPrinter(Int.MaxValue, 0)
	val expectedString = pp.format(expected)
	val actualString = pp.formatNodes(actual)
	assert(expectedString == actualString, "Expected dependencies section:\n" + expectedString + "\n\nActual:\n" + actualString)
}
