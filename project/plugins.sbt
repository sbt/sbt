scalaVersion := "2.10.6"
scalacOptions ++= Seq("-feature", "-language:postfixOps")

addSbtPlugin("com.eed3si9n" % "sbt-doge" % "0.1.5")
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.1.14")
addSbtPlugin("com.typesafe.sbt" % "sbt-ghpages" % "0.6.0")
addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "0.9.2")
addSbtPlugin("com.typesafe.sbt" % "sbt-javaversioncheck" % "0.1.0")
addSbtPlugin("com.geirsson" % "sbt-scalafmt" % "0.6.8")
addSbtPlugin("com.typesafe.sbt" % "sbt-site" % "1.2.0")
addSbtPlugin("org.foundweekends" % "sbt-bintray" % "0.4.0")
addSbtPlugin("org.scala-sbt" % "sbt-contraband" % "0.3.0-M4")
