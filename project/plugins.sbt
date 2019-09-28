
plugins_(
  "com.geirsson"       % "sbt-ci-release"           % "1.4.31",
  "com.typesafe"       % "sbt-mima-plugin"          % "0.3.0",
  "org.scala-sbt"      % "sbt-contraband"           % "0.4.4"
)

libs ++= Seq(
  "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value
)

addSbtPlugin("io.get-coursier" % "sbt-coursier" % "2.0.0-RC3-6")
addSbtPlugin("io.get-coursier" % "sbt-shading" % "2.0.0-RC3-6")


def plugins_(modules: ModuleID*) = modules.map(addSbtPlugin)
def libs = libraryDependencies
