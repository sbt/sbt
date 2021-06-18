ThisBuild / useCoursier := false

scalacOptions ++= Seq("-feature", "-language:postfixOps", "-Ywarn-unused:_,-imports")

addSbtPlugin("com.dwijnand" % "sbt-dynver" % "4.0.0")
addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.1.2")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.3.0")
addSbtPlugin("org.scala-sbt" % "sbt-contraband" % "0.5.1")
addSbtPlugin("de.heikoseeberger" % "sbt-header" % "3.0.2")
addSbtPlugin("com.lightbend" % "sbt-whitesource" % "0.1.14")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.9")
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.8.1")
addSbtPlugin("com.swoval" % "sbt-java-format" % "0.3.1")
