lazy val p1 = (project in file("p1")).
  settings(
    checkTask(expectedMongo),
    libraryDependencies += "org.mongodb" %% "casbah" % "2.4.1" pomOnly(),
    inThisBuild(List(
      organization := "org.example",
      version := "1.0",
      scalaVersion := "2.9.2",
      autoScalaLibrary := false
    ))
  )

lazy val p2 = (project in file("p2")).
  dependsOn(p1).
  settings(
    checkTask(expectedInter)
  )

lazy val expectedMongo =
  <dependency>
    <groupId>org.mongodb</groupId>
    <artifactId>casbah_2.9.2</artifactId>
    <version>2.4.1</version>
    <type>pom</type>
  </dependency>

lazy val expectedInter =
  <dependency>
    <groupId>org.example</groupId>
    <artifactId>p1_2.9.2</artifactId>
    <version>1.0</version>
  </dependency>

def checkTask(expectedDep: xml.Elem) = TaskKey[Unit]("checkPom") := {
  val file = makePom.value
  val pom = xml.XML.loadFile(file)
  val actual = pom \\ "dependencies"
  val expected = <d>
    {expectedDep}
  </d>
  def dropTopElem(s:String): String = s.split("""\n""").drop(1).dropRight(1).mkString("\n")
  val pp = new xml.PrettyPrinter(Int.MaxValue, 0)
  val expectedString = dropTopElem(pp.format(expected))
  val actualString = dropTopElem(pp.formatNodes(actual))
  assert(expectedString == actualString, "Expected dependencies section:\n" + expectedString + "\n\nActual:\n" + actualString)
}
