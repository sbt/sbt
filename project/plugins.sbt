Global / semanticdbVersion := "4.15.2"
scalacOptions ++= Seq("-feature", "-language:postfixOps", "-Ywarn-unused:_,-imports")

addSbtPlugin("com.github.sbt" % "sbt-dynver" % "5.1.1")
addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.3.1")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.6")
addSbtPlugin("org.scala-sbt" % "sbt-contraband" % "0.8.0")
addSbtPlugin("com.github.sbt" % "sbt-header" % "5.11.0")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.3.1")
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "1.1.4")
addSbtPlugin("com.swoval" % "sbt-java-format" % "0.3.1")
addSbtPlugin("org.scalameta" % "sbt-native-image" % "0.3.4")
addDependencyTreePlugin
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.14.5")
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.11.4")

// libraryDependencies += "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value
