
plugins_(
  "io.get-coursier"   % "sbt-coursier"    % coursierVersion,
  "com.typesafe"      % "sbt-mima-plugin" % "0.1.15",
  "org.xerial.sbt"    % "sbt-pack"        % "0.8.2",
  "com.jsuereth"      % "sbt-pgp"         % "1.0.1",
  "com.typesafe.sbt"  % "sbt-proguard"    % "0.2.3",
  "com.github.gseitz" % "sbt-release"     % "1.0.5",
  "org.scala-js"      % "sbt-scalajs"     % "0.6.19",
  "org.scoverage"     % "sbt-scoverage"   % "1.4.0",
  "io.get-coursier"   % "sbt-shading"     % coursierVersion,
  "org.xerial.sbt"    % "sbt-sonatype"    % "1.1",
  "com.timushev.sbt"  % "sbt-updates"     % "0.3.0",
  "org.tpolecat"      % "tut-plugin"      % "0.5.2"
)

libs ++= Seq(
  "org.scala-sbt" % "scripted-plugin" % sbtVersion.value,
  compilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full), // for shapeless / auto type class derivations
  "com.github.alexarchambault" %% "argonaut-shapeless_6.2" % "1.2.0-M5"
)

// required for just released things
resolvers += Resolver.sonatypeRepo("releases")


def plugins_(modules: ModuleID*) = modules.map(addSbtPlugin)
def libs = libraryDependencies
