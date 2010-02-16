import sbt._

import java.net.URL

class Plugins(info: ProjectInfo) extends PluginDefinition(info)
{
	val scripted = "org.scala-tools.sbt" % "scripted" % "0.7.0"
	val t_repo = "t_repo" at "http://tristanhunt.com:8081/content/groups/public/"
	val posterous = "net.databinder" % "posterous-sbt" % "0.1.3"
	val technically = Resolver.url("technically.us", new URL("http://databinder.net/repo/"))(Resolver.ivyStylePatterns)
}
