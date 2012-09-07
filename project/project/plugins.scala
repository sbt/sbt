import sbt._
import Keys._

object PluginBuild extends Build {
  override def projects = Seq(root)
  
  val root = Project("root", file(".")) settings(
    resolvers += Resolver.url("scalasbt", new URL("http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-releases"))(Resolver.ivyStylePatterns),
    addSbtPlugin("com.typesafe" % "sbt-native-packager" % "0.4.3"),
    libraryDependencies += "net.databinder" % "dispatch-http_2.9.1" % "0.8.6"
  )
}
