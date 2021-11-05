addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.5.10")
addSbtPlugin("io.github.alexarchambault.sbt" % "sbt-compatibility" % "0.0.8")
addSbtPlugin("io.github.alexarchambault.sbt" % "sbt-eviction-rules" % "0.2.0")
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.8.1")
addSbtPlugin("io.get-coursier" % "sbt-shading" % "2.0.1")

libraryDependencies += "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value
