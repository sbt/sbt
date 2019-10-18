addSbtPlugin("com.geirsson" % "sbt-ci-release" % "1.4.31")
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.3.0")

addSbtPlugin("io.get-coursier" % "sbt-coursier" % "2.0.0-RC4")
addSbtPlugin("io.get-coursier" % "sbt-shading" % "2.0.0-RC4")

libraryDependencies += "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value
