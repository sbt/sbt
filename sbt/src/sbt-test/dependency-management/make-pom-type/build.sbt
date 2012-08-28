scalaVersion := "2.9.2"

libraryDependencies +=  "org.mongodb" %% "casbah" % "2.4.1" pomOnly()

autoScalaLibrary := false


TaskKey[Unit]("check-pom") <<= makePom map { file =>
	val pom = xml.XML.loadFile(file)
	val actual = pom \\ "dependencies"
	val expectedDep = 
		<dependency>
			<groupId>org.mongodb</groupId>
			<artifactId>casbah_2.9.2</artifactId>
			<version>2.4.1</version>
			<type>pom</type>
		</dependency>
	val expected = <d>
		{expectedDep}
	</d>
	def dropTopElem(s:String): String = s.split("""\n""").drop(1).dropRight(1).mkString("\n")
	val pp = new xml.PrettyPrinter(Int.MaxValue, 0)
	val expectedString = dropTopElem(pp.format(expected))
	val actualString = dropTopElem(pp.formatNodes(actual))
	assert(expectedString == actualString, "Expected dependencies section:\n" + expectedString + "\n\nActual:\n" + actualString)
}
