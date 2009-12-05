import sbt._

class Plugins(info: ProjectInfo) extends PluginDefinition(info)
{
	val scripted = "org.scala-tools.sbt" % "test" % "0.6.5"
}
