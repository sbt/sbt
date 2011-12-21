import sbt._
import Keys._

object PluginBuild extends Build {
  override def projects = Seq(root)
  
  val root = Project("root", file(".")) dependsOn(packager) settings(libraryDependencies += "net.databinder" %% "dispatch-http" % "0.8.6")
  
  lazy val packager = RootProject(file("/home/jsuereth/projects/typesafe/sbt-native-packager"))
}
