import sbt._
class Plugins(info: ProjectInfo) extends PluginDefinition(info)
{
  val a = "antlr" % "antlr" % "2.7.7"
}