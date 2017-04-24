
plugins_(
  "io.get-coursier"   % "sbt-coursier"    % coursierVersion,
  "com.typesafe"      % "sbt-mima-plugin" % "0.1.13",
  "org.xerial.sbt"    % "sbt-pack"        % "0.8.2",
  "com.jsuereth"      % "sbt-pgp"         % "1.0.0",
  "com.typesafe.sbt"  % "sbt-proguard"    % "0.2.2",
  "com.github.gseitz" % "sbt-release"     % "1.0.4",
  "org.scala-js"      % "sbt-scalajs"     % "0.6.15",
  "org.scoverage"     % "sbt-scoverage"   % "1.4.0",
  "io.get-coursier"   % "sbt-shading"     % coursierVersion,
  "org.xerial.sbt"    % "sbt-sonatype"    % "1.1",
  "org.tpolecat"      % "tut-plugin"      % "0.4.8"
)

libs += "org.scala-sbt" % "scripted-plugin" % sbtVersion.value

// important: this line is matched / substituted during releases (via sbt-release)
def coursierVersion = "1.0.0-RC1"


def plugins_(modules: ModuleID*) = modules.map(addSbtPlugin)
def libs = libraryDependencies
