import sbt._

import java.net.URL

class Plugins(info: ProjectInfo) extends PluginDefinition(info)
{
	val scripted = "org.scala-tools.sbt" % "scripted" % "0.7.0"
//	val posterous = "net.databinder" % "posterous-sbt" % "0.1.0-SNAPSHOT"
	val technically = Resolver.url("technically.us", new URL("http://databinder.net/repo/"))(Resolver.ivyStylePatterns)
}
