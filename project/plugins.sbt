scalaVersion := "2.12.8"
scalacOptions ++= Seq("-feature", "-language:postfixOps")

addSbtPlugin("org.scala-sbt"     % "sbt-houserules"  % "0.3.9")
addSbtPlugin("org.scalameta"     % "sbt-scalafmt"    % "2.0.2")
addSbtPlugin("io.spray"          % "sbt-boilerplate" % "0.6.1")
addSbtPlugin("org.scala-sbt"     % "sbt-contraband"  % "0.4.1")
addSbtPlugin("de.heikoseeberger" % "sbt-header"      % "3.0.2")
addSbtPlugin("com.eed3si9n"      % "sbt-buildinfo"   % "0.9.0")
addSbtPlugin("com.lightbend"     % "sbt-whitesource" % "0.1.14")
addSbtPlugin("com.eed3si9n"      % "sbt-assembly"    % "0.14.9")
