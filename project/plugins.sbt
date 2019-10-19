addSbtPlugin("com.dwijnand" % "sbt-dynver" % "4.0.0")
addSbtPlugin("org.foundweekends" % "sbt-bintray" % "0.5.5")
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "2.0.0")
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.6.1")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.0.2")
addSbtPlugin("org.scala-sbt"  % "sbt-contraband"  % "0.4.2")
addSbtPlugin("com.lightbend"  % "sbt-whitesource" % "0.1.14")

scalacOptions += "-language:postfixOps"
