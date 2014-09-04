organization := "com.sksamuel.akka"

name := "akka-patterns"

version := "0.11.0"

scalaVersion := "2.11.2"

crossScalaVersions := Seq("2.11.2", "2.10.4")

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

javacOptions ++= Seq("-source", "1.6", "-target", "1.6")

publishTo <<= version {
  (v: String) =>
    val nexus = "https://oss.sonatype.org/"
    if (v.trim.endsWith("SNAPSHOT"))
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

publishMavenStyle := true

publishArtifact in Test := false

parallelExecution in Test := false

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.3.3"

libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % "2.3.3"

libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.7"

libraryDependencies += "org.slf4j" % "log4j-over-slf4j" % "1.7.7" % "test"

libraryDependencies += "log4j" % "log4j" % "1.2.17" % "test"

libraryDependencies += "commons-io" % "commons-io" % "2.4"

libraryDependencies += "org.mockito" % "mockito-all" % "1.9.5" % "test"

libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.0" % "test"

pomExtra := (
  <url>https://github.com/sksamuel/akka-patterns</url>
    <licenses>
      <license>
        <name>Apache 2</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0</url>
        <distribution>repo</distribution>
      </license>
    </licenses>
    <scm>
      <url>git@github.com:sksamuel/akka-patterns.git</url>
      <connection>scm:git@github.com:sksamuel/akka-patterns.git</connection>
    </scm>
    <developers>
      <developer>
        <id>sksamuel</id>
        <name>sksamuel</name>
        <url>http://github.com/akka-patterns</url>
      </developer>
    </developers>)
