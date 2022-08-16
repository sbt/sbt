addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.5.10")
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "1.1.0")
addSbtPlugin("io.get-coursier" % "sbt-shading" % "2.1.0")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.9.34")

libraryDependencies += "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value
