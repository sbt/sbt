addSbtPlugin("org.scala-sbt" % "sbt-houserules" % "0.3.3")
addSbtPlugin("org.scala-sbt" % "sbt-contraband" % "0.3.0")
addSbtPlugin("com.lucidchart" % "sbt-scalafmt" % "1.10")
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.1.17")

scalacOptions += "-language:postfixOps"
