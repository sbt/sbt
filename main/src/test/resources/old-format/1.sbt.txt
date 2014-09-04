import SonatypeKeys._

xerial.sbt.Sonatype.sonatypeSettings

organization := "me.benoitguigal"

name := "twitter"

version := "1.1-SNAPSHOT"

scalaVersion := "2.10.2"

resolvers += "spray repo" at "http://repo.spray.io"

libraryDependencies ++= {
  val sprayV = "1.2.1"
  Seq(
    "com.typesafe.akka"  %% "akka-actor"     % "2.2.3",
    "joda-time"          %  "joda-time"      % "2.3",
    "org.joda"           % "joda-convert"    % "1.2",
    "io.spray"           % "spray-http"      % sprayV,
    "io.spray"           % "spray-httpx"     % sprayV,
    "io.spray"           % "spray-util"      % sprayV,
    "io.spray"           % "spray-client"    % sprayV,
    "io.spray"           % "spray-can"       % sprayV,
    "com.netflix.rxjava" % "rxjava-scala"    % "0.19.6",
    "io.spray"           %% "spray-json"     % "1.2.6",
    "org.scalatest"      % "scalatest_2.10"  % "2.1.3" % "test",
    "org.mockito"        % "mockito-core"    % "1.9.5" % "test")
}

publishMavenStyle := true

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

pomIncludeRepository := { _ => false }

pomExtra := (
  <url>https://github.com/benoitguigal/twitter-spray</url>
  <licenses>
    <license>
      <name>BSD-style</name>
      <url>http://www.opensource.org/licenses/bsd-license.php</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <scm>
    <url>git@github.com:benoitguigal/twitter-spray.git</url>
    <connection>scm:git:git@github.com:benoitguigal/twitter-spray.git</connection>
  </scm>
  <developers>
    <developer>
      <id>BGuigal</id>
      <name>Benoit Guigal</name>
      <url>http://benoitguigal.me</url>
    </developer>
  </developers>
  )


