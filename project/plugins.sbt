scalaVersion := "2.12.4"
scalacOptions ++= Seq("-feature", "-language:postfixOps")

addSbtPlugin("org.scala-sbt"     % "sbt-houserules" % "0.3.5")
addSbtPlugin("org.scala-sbt"     % "sbt-contraband" % "0.3.3")
addSbtPlugin("de.heikoseeberger" % "sbt-header"     % "3.0.2")
addSbtPlugin("com.eed3si9n"      % "sbt-buildinfo"  % "0.8.0")
