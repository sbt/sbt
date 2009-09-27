
import sbt._

class TestUse(info: ProjectInfo) extends DefaultProject(info)
{
	override def managedStyle = ManagedStyle.Ivy
	val publishTo = Resolver.file("test-repo", path("repo").asFile)(Patterns(false, Resolver.mavenStyleBasePattern))
	val mavenC = "org.example" % "test-ivy" % "1.0"
	val mavenT = "org.example" % "test-ivy" % "1.0" % "test"
	val mavenR = "org.example" % "test-ivy" % "1.0" % "runtime"
}