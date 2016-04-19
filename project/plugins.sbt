addSbtPlugin("org.xerial.sbt" % "sbt-pack" % "0.6.8")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.8")
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.0.0")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.1.0")
addSbtPlugin("org.tpolecat" % "tut-plugin" % "0.4.0")
addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0-M11")
addSbtPlugin("com.typesafe.sbt" % "sbt-proguard" % "0.2.2")
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.1.8")
libraryDependencies += "org.scala-sbt" % "scripted-plugin" % sbtVersion.value
