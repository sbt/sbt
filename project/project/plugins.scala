import sbt._
import Keys._

object PluginBuild extends Build {
  override def projects = Seq(root)
  
  val root = Project("root", file(".")) settings(
    resolvers += Resolver.url("scalasbt", new URL("http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-releases"))(Resolver.ivyStylePatterns),
    addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.1.1"),
    libraryDependencies += "net.databinder" %% "dispatch-http" % "0.8.10"
  ) //dependsOn(nativePackager)


  //lazy val nativePackager = uri("file:///C:/projects/sbt-native-packager")
}
