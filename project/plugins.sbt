addSbtPlugin("org.xerial.sbt" % "sbt-pack" % "0.8.0")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.11")
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.0.0")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.1.0")
addSbtPlugin("org.tpolecat" % "tut-plugin" % "0.4.2")
addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0-M13")
addSbtPlugin("com.typesafe.sbt" % "sbt-proguard" % "0.2.2")
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.1.9")
libraryDependencies += "org.scala-sbt" % "scripted-plugin" % sbtVersion.value
