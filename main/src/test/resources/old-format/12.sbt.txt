name := "play-recaptcha"

description := "Google reCAPTCHA integration for Play Framework"

organization := "com.nappin"

version := "0.9-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

crossScalaVersions := Seq("2.10.4", "2.11.1")

libraryDependencies ++= Seq(
  ws,
  "org.mockito" % "mockito-core" % "1.+" % "test"
)

// needed to publish to maven central
publishMavenStyle := true

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

pomExtra := (
  <url>http://chrisnappin.github.io/play-recaptcha</url>
  <licenses>
    <license>
      <name>Apache License, Version 2.0</name>
      <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <scm>
    <connection>scm:git:git@github.com:chrisnappin/play-recaptcha.git</connection>
    <developerConnection>scm:git:git@github.com:chrisnappin/play-recaptcha.git</developerConnection>
    <url>git@github.com:chrisnappin/play-recaptcha.git</url>
  </scm>
  <developers>
    <developer>
      <id>chrisnappin</id>
      <name>Chris Nappin</name>
      <email>chris@nappin.com</email>
      <timezone>UTC</timezone>
    </developer>
  </developers>)
  