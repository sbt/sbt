addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.5.12")
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "1.1.3")
addSbtPlugin("io.get-coursier" % "sbt-shading" % "2.1.3")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.10.4")

libraryDependencies += "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value
