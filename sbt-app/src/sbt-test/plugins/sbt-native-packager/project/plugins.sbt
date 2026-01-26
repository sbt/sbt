ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always

// addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.20")
libraryDependencies += Defaults.sbtPluginExtra("com.github.sbt" % "sbt-native-packager" % "1.11.4", "2", "3.8.1")
