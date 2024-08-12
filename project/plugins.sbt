addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.6.0")
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "1.1.4")
addSbtPlugin("io.get-coursier" % "sbt-shading" % "2.1.5")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.12.1")

libraryDependencies += "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value
