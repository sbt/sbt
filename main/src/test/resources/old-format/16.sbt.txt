import sbt._
import AssemblyKeys._
import aether.Aether._

name := "conjecture"

version := "0.1.2-SNAPSHOT"

organization := "com.etsy"

scalaVersion := "2.9.3"

sbtVersion := "0.12.1"

scalacOptions ++= Seq("-unchecked", "-deprecation")

compileOrder := CompileOrder.JavaThenScala

javaHome := Some(file("/usr/java/latest"))

publishArtifact in packageDoc := false

resolvers ++= {
  Seq(
      "Concurrent Maven Repo" at "http://conjars.org/repo",
      "cloudera" at "https://repository.cloudera.com/artifactory/cloudera-repos/"
  )
}

libraryDependencies += "cascading" % "cascading-core" % "2.0.0"

libraryDependencies += "cascading" % "cascading-local" % "2.0.0" exclude("com.google.guava", "guava")

libraryDependencies += "cascading" % "cascading-hadoop" % "2.0.0"

libraryDependencies += "cascading.kryo" % "cascading.kryo" % "0.4.6"

libraryDependencies += "com.google.code.gson" % "gson" % "2.2.2"

libraryDependencies += "com.twitter" % "maple" % "0.2.4"

libraryDependencies += "com.twitter" % "algebird-core_2.9.2" % "0.1.12"

libraryDependencies += "com.twitter" % "scalding-core_2.9.2" % "0.8.5"

libraryDependencies += "commons-lang" % "commons-lang" % "2.4"

libraryDependencies += "com.joestelmach" % "natty" % "0.7"

libraryDependencies += "io.backchat.jerkson" % "jerkson_2.9.2" % "0.7.0"

libraryDependencies += "com.google.guava" % "guava" % "13.0.1"

libraryDependencies += "org.apache.commons" % "commons-math3" % "3.2"

libraryDependencies += "org.apache.hadoop" % "hadoop-common" % "2.0.0-cdh4.1.1" exclude("commons-daemon", "commons-daemon")

libraryDependencies += "org.apache.hadoop" % "hadoop-hdfs" % "2.0.0-cdh4.1.1" exclude("commons-daemon", "commons-daemon")

libraryDependencies += "org.apache.hadoop" % "hadoop-tools" % "2.0.0-mr1-cdh4.1.1" exclude("commons-daemon", "commons-daemon")

libraryDependencies += "net.sf.trove4j" % "trove4j" % "3.0.3"

libraryDependencies += "com.esotericsoftware.kryo" % "kryo" % "2.21"

libraryDependencies += "com.novocode" % "junit-interface" % "0.10" % "test"

parallelExecution in Test := false

publishArtifact in Test := false

seq(assemblySettings: _*)

publishTo <<= version { v : String =>
  val nexus = "https://oss.sonatype.org/"
  if (v.trim.endsWith("SNAPSHOT")) {
    Some("snapshots" at nexus + "content/repositories/snapshots")
  } else {
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
  }
}

publishMavenStyle := true

pomIncludeRepository := { x => false }

pomExtra := (
  <url>https://github.com/etsy/Conjecture</url>
  <licenses>
    <license>
      <name>MIT License</name>
      <url>http://opensource.org/licenses/MIT</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <scm>
    <url>git@github.com:etsy/Conjecture.git</url>
    <connection>scm:git:git@github.com:etsy/Conjecture.git</connection>
  </scm>
  <developers>
    <developer>
      <id>jattenberg</id>
      <name>Josh Attenberg</name>
      <url>github.com/jattenberg</url>
    </developer>
    <developer>
      <id>rjhall</id>
      <name>Rob Hall</name>
      <url>github.com/rjhall</url>
    </developer>
  </developers>
)


credentials += Credentials(Path.userHome / ".sbt" / ".credentials")


seq(aetherPublishSettings: _*)

pomIncludeRepository := { _ => false }

// Uncomment if you don't want to run all the tests before building assembly
// test in assembly := {}

// Janino includes a broken signature, and is not needed:
excludedJars in assembly <<= (fullClasspath in assembly) map { cp =>
  val excludes = Set("jsp-api-2.1-6.1.14.jar", "jsp-2.1-6.1.14.jar",
    "jasper-compiler-5.5.12.jar", "janino-2.5.16.jar")
  cp filter { jar => excludes(jar.data.getName)}
}

// Some of these files have duplicates, let's ignore:
mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) =>
  {
    case s if s.endsWith(".class") => MergeStrategy.last
    case s if s.endsWith("project.clj") => MergeStrategy.concat
    case s if s.endsWith(".html") => MergeStrategy.last
    case s if s.contains("servlet") => MergeStrategy.last
    case x => old(x)
  }
}
