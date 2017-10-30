scalaVersion := "2.12.3"
scalacOptions ++= Seq("-feature", "-language:postfixOps")

addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.1.17")
// addSbtPlugin("com.typesafe.sbt" % "sbt-ghpages" % "0.6.0")
// addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "0.9.2")
// addSbtPlugin("com.typesafe.sbt" % "sbt-javaversioncheck" % "0.1.0")
// addSbtPlugin("com.typesafe.sbt" % "sbt-site" % "1.2.0")

addSbtPlugin("org.foundweekends" % "sbt-bintray" % "0.5.1")
addSbtPlugin("org.scala-sbt" % "sbt-contraband" % "0.3.1")
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.0-M1")
addSbtPlugin("com.lucidchart" % "sbt-scalafmt" % "1.14")
addSbtPlugin("de.heikoseeberger" % "sbt-header" % "3.0.2")
