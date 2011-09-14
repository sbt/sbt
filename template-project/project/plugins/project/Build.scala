import sbt._

object PluginDef extends Build {
  override def projects = Seq(root)
  lazy val root = Project("plugins", file(".")) dependsOn(extras)
  lazy val extras = uri("git://github.com/paulp/sbt-extras")
}   
