import sbt._

class TestPublish(info: ProjectInfo) extends DefaultProject(info)
{
	override def managedStyle = ManagedStyle.Maven // the default, but make it explicit
	val publishTo = Resolver.file("test-repo", path("repo").asFile)
}