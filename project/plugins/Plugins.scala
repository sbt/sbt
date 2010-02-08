import sbt._

import java.net.URL

class Plugins(info: ProjectInfo) extends PluginDefinition(info)
{
	val scripted = "org.scala-tools.sbt" % "scripted" % "0.6.13"
	val technically = Resolver.url("technically.us", new URL("http://databinder.net/repo/"))(Resolver.ivyStylePatterns)
}
