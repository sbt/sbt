addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.5.10")
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.9.2")
addSbtPlugin("io.get-coursier" % "sbt-shading" % "2.0.1")

libraryDependencies += "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value
