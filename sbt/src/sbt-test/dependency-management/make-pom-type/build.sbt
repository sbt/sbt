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
	val expected = <dependencies xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns="http://maven.apache.org/POM/4.0.0" >
		{expectedDep}
	</dependencies>
	val pp = new xml.PrettyPrinter(Int.MaxValue, 0)
	val expectedString = pp.format(expected)
	val actualString = pp.formatNodes(actual)
	assert(expectedString == actualString, "Expected dependencies section:\n" + expectedString + "\n\nActual:\n" + actualString)
}
