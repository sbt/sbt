addSbtPlugin("org.scala-sbt"  % "sbt-houserules"  % "0.3.9")
addSbtPlugin("org.scalameta"  % "sbt-scalafmt"    % "2.0.0")
addSbtPlugin("org.scala-sbt"  % "sbt-contraband"  % "0.4.2")
addSbtPlugin("com.lightbend"  % "sbt-whitesource" % "0.1.14")

scalacOptions += "-language:postfixOps"
