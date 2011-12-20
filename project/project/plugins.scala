import sbt._

object PluginBuild extends Build {
  override def projects = Seq(root)
  
  val root = Project("root", file(".")) dependsOn(packager)
  
  lazy val packager = RootProject(file("/home/jsuereth/projects/typesafe/sbt-native-packager"))
}
