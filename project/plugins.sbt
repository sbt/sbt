scalacOptions ++= Seq("-feature", "-language:postfixOps", "-Ywarn-unused:_,-imports")

addSbtPlugin("com.github.sbt" % "sbt-dynver" % "5.0.1")
addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.1.2")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.2")
addSbtPlugin("org.scala-sbt" % "sbt-contraband" % "0.5.3")
addSbtPlugin("de.heikoseeberger" % "sbt-header" % "5.6.5")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.0.0-RC1")
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "1.1.4")
addSbtPlugin("com.swoval" % "sbt-java-format" % "0.3.1")
addSbtPlugin("org.scalameta" % "sbt-native-image" % "0.3.1")
addDependencyTreePlugin
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.13.0")

// libraryDependencies += "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value
