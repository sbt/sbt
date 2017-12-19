scalaVersion := "2.12.4"
scalacOptions ++= Seq("-feature", "-language:postfixOps")

addSbtPlugin("org.scala-sbt" % "sbt-houserules" % "0.3.4")
addSbtPlugin("org.scala-sbt" % "sbt-contraband" % "0.3.2")
addSbtPlugin("com.lucidchart" % "sbt-scalafmt" % "1.14")
addSbtPlugin("de.heikoseeberger" % "sbt-header" % "3.0.2")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.7.0")
