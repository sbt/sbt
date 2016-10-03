libraryDependencies ++= Seq("natives-windows", "natives-linux", "natives-osx") map ( c =>
	"org.lwjgl.lwjgl" % "lwjgl-platform" % "2.8.2" classifier c
)

autoScalaLibrary := false

TaskKey[Unit]("check") := (dependencyClasspath in Compile map { cp =>
	assert(cp.size == 3, "Expected 3 jars, got: " + cp.files.mkString("(", ", ", ")"))
}).value

TaskKey[Unit]("checkPom") := {
	val file = makePom.value
	val pom = xml.XML.loadFile(file)
	val actual = pom \\ "dependencies"
	def depSection(classifier: String) =
		<dependency>
			<groupId>org.lwjgl.lwjgl</groupId>
			<artifactId>lwjgl-platform</artifactId>
			<version>2.8.2</version>
			<classifier>{classifier}</classifier>
		</dependency>
	val sections = <d>
{depSection("natives-windows") ++ depSection("natives-linux") ++ depSection("natives-osx")}
</d>
	def dropTopElem(s:String): String = s.split("""\n""").drop(1).dropRight(1).mkString("\n")
	val pp = new xml.PrettyPrinter(Int.MaxValue, 0)
	val expectedString = dropTopElem(pp.formatNodes(sections))
	val actualString = dropTopElem(pp.formatNodes(actual))
	assert(expectedString == actualString, "Expected dependencies section:\n" + expectedString + "\n\nActual:\n" + actualString)
}
