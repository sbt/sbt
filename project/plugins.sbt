
plugins_(
  "io.get-coursier"  % "sbt-coursier"    % "1.0.0-RC1",
  "com.typesafe"     % "sbt-mima-plugin" % "0.1.13",
  "org.xerial.sbt"   % "sbt-pack"        % "0.8.2",
  "com.jsuereth"     % "sbt-pgp"         % "1.0.0",
  "com.typesafe.sbt" % "sbt-proguard"    % "0.2.2",
  "org.scala-js"     % "sbt-scalajs"     % "0.6.15",
  "org.scoverage"    % "sbt-scoverage"   % "1.4.0",
  "io.get-coursier"  % "sbt-shading"     % "1.0.0-RC1",
  "org.tpolecat"     % "tut-plugin"      % "0.4.8"
)

libs += "org.scala-sbt" % "scripted-plugin" % sbtVersion.value


def plugins_(modules: ModuleID*) = modules.map(addSbtPlugin)
def libs = libraryDependencies
