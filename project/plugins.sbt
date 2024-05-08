ThisBuild / useCoursier := false

scalacOptions ++= Seq("-feature", "-language:postfixOps", "-Ywarn-unused:_,-imports")

addSbtPlugin("com.github.sbt" % "sbt-dynver" % "5.0.1")
addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.1.2")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.3.0")
addSbtPlugin("org.scala-sbt" % "sbt-contraband" % "0.5.3")
addSbtPlugin("de.heikoseeberger" % "sbt-header" % "5.6.5")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "1.2.0")
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.8.1")
addSbtPlugin("com.swoval" % "sbt-java-format" % "0.3.1")
addSbtPlugin("org.scalameta" % "sbt-native-image" % "0.3.1")
addDependencyTreePlugin
