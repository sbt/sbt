sbtPlugin := true

name := "sbt-launcher-package"

organization := "org.scala-sbt"

version := "0.1.0"

crossTarget <<= target

publishTo in Global := {
   val nativeReleaseUrl = "http://scalasbt.artifactoryonline.com/scalasbt/sbt-native-packages"
   val nativeReleasePattern = "[organization]/[module]/[revision]/[module].[ext]"
   val resolver = Resolver.url("native-releases", new URL(nativeReleaseUrl))(Patterns(nativeReleasePattern))
//     Resolver.file("native-releases-local", file("/home/jsuereth/repos/native-packages"))(Patterns(nativeReleasePattern))
  Some(resolver)
}

