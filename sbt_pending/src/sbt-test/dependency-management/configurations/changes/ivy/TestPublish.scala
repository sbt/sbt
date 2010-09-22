import sbt._

class TestPublish(info: ProjectInfo) extends DefaultProject(info)
{
	override def managedStyle = ManagedStyle.Ivy
	val publishTo = Resolver.file("test-repo", path("repo").asFile)(Patterns(false, Resolver.mavenStyleBasePattern))
}