import AssemblyKeys._ 
assemblySettings

/** Project */
name := "signal-collect-yarn"

version := "1.0-SNAPSHOT"

organization := "com.signalcollect"

scalaVersion := "2.11.1"

val hadoopVersion = "2.3.0"

net.virtualvoid.sbt.graph.Plugin.graphSettings

scalacOptions ++= Seq("-optimize", "-Yinline-warnings", "-feature", "-deprecation", "-Xelide-below", "INFO" )

EclipseKeys.createSrc := EclipseCreateSrc.Default + EclipseCreateSrc.Resource

EclipseKeys.withSource := true

parallelExecution in Test := false

test in assembly := {}

mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) =>
  {
    case PathList("org", "apache", "hadoop", xs @ _*) => MergeStrategy.last
    case PathList("org", "apache", "commons", "collections", xs @ _*) => MergeStrategy.last
    case PathList("org", "objectweb", "asm", xs @ _*) => MergeStrategy.last
    case PathList("com", "thoughtworks", xs @ _*) => MergeStrategy.last
    case PathList("META-INF", "maven", xs @ _*) => MergeStrategy.last
    case PathList("log4j.properties") => MergeStrategy.last
    case x => old(x)
  }
}

excludedJars in assembly <<= (fullClasspath in assembly) map { cp => 
  cp filter { entry =>
    (entry.data.getName == "asm-3.2.jar" ||
     entry.data.getName == "asm-3.1.jar"
   )}
}

/** Dependencies */
libraryDependencies ++= Seq(  
  "org.scala-lang.modules" %% "scala-async" % "0.9.1",
  "org.scala-lang" % "scala-library" % "2.11.1" % "compile",
 ("org.apache.hadoop" % "hadoop-common" % hadoopVersion % "compile").
   exclude("commons-beanutils", "commons-beanutils-core"),
  "org.apache.hadoop" % "hadoop-yarn-common" % hadoopVersion % "compile",
  ("org.apache.hadoop" % "hadoop-yarn-client" % hadoopVersion % "compile").
  exclude("hadoop-yarn-api", "org.apache.hadoop"),
  "org.apache.hadoop" % "hadoop-yarn-server-resourcemanager" % hadoopVersion % "compile",
  "org.apache.hadoop" % "hadoop-yarn-server-nodemanager" % hadoopVersion % "compile",
  "org.apache.hadoop" % "minicluster" % "2.2.0"	,
  "org.apache.hadoop" % "hadoop-hdfs" % hadoopVersion,
  "com.github.romix.akka" %% "akka-kryo-serialization-custom" % "0.3.5" % "compile",
  "com.amazonaws" % "aws-java-sdk" % "1.7.12" % "compile",
  "com.jcraft" % "jsch" % "0.1.51" % "compile",
  "org.apache.commons" % "commons-compress" % "1.5" % "compile",
  "log4j" % "log4j" % "1.2.17" % "compile",
  "com.typesafe.scala-logging" %% "scala-logging-slf4j" % "2.1.2",
  "com.typesafe.akka" % "akka-slf4j_2.11" % "2.3.4",
  "junit" % "junit" % "4.8.2"  % "test",
  "org.specs2" %% "specs2" % "2.3.11"  % "test",
  "org.scalacheck" %% "scalacheck" % "1.11.3" % "test",
  "org.scalatest" %% "scalatest" % "2.1.3" % "test",
  "org.easymock" % "easymock" % "3.2" % "test",
  "com.typesafe.akka" %% "akka-remote" % "2.3.4" force()
)

resolvers += "Ifi Public" at "https://maven.ifi.uzh.ch/maven2/content/groups/public/"
