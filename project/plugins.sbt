addSbtPlugin("com.geirsson" % "sbt-ci-release" % "1.5.3")
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.7.0")

addSbtPlugin("io.get-coursier" % "sbt-shading" % "2.0.0")

libraryDependencies += "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value
