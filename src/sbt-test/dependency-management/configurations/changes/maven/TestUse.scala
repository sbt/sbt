
import sbt._

class TestUse(info: ProjectInfo) extends DefaultProject(info)
{
	val publishTo = Resolver.file("test-repo", path("repo").asFile)
	val mavenC = "org.example" % "test" % "1.0"
	val mavenT = "org.example" % "test" % "1.0" % "test"
	val mavenR = "org.example" % "test" % "1.0" % "runtime"
}