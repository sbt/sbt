Global / semanticdbVersion := "4.15.2"
scalacOptions ++= Seq("-feature", "-Ywarn-unused:_,-imports")

addSbtPlugin("com.github.sbt" % "sbt-dynver" % "5.1.1")
addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.3.1")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.6.0")
addSbtPlugin("org.scala-sbt" % "sbt-contraband" % "0.9.0")
addSbtPlugin("com.github.sbt" % "sbt-header" % "5.11.0")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.3.1")
addSbtPlugin("com.eed3si9n" % "sbt-salad-days" % "0.1.0")
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "1.1.5")
addSbtPlugin("com.github.sbt" % "sbt-java-formatter" % "0.12.0")
addSbtPlugin("org.scalameta" % "sbt-native-image" % "0.4.0")
addDependencyTreePlugin
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.14.5")
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.11.7")
addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.4.8")

// libraryDependencies += "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value
