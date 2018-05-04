scalaVersion := "2.12.6"
scalacOptions ++= Seq("-feature", "-language:postfixOps")

addSbtPlugin("com.typesafe"      % "sbt-mima-plugin" % "0.2.0")
addSbtPlugin("org.scala-sbt"     % "sbt-houserules"  % "0.3.6")
addSbtPlugin("org.scala-sbt"     % "sbt-contraband"  % "0.4.0")
addSbtPlugin("de.heikoseeberger" % "sbt-header"      % "3.0.2")
addSbtPlugin("com.eed3si9n"      % "sbt-buildinfo"   % "0.8.0")
addSbtPlugin("com.lucidchart"    % "sbt-scalafmt"    % "1.15")
addSbtPlugin("com.lightbend"     % "sbt-whitesource" % "0.1.9")
