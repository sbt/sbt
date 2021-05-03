addSbtPlugin("com.dwijnand" % "sbt-dynver" % "4.0.0")
addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.1.2")
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.8.1")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.0.2")
addSbtPlugin("org.scala-sbt" % "sbt-contraband" % "0.5.1")
addSbtPlugin("com.lightbend" % "sbt-whitesource" % "0.1.14")

scalacOptions += "-language:postfixOps"
