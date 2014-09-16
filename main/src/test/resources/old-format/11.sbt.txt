name := "core"

resolvers += "spray repo" at "http://repo.spray.io"

libraryDependencies ++= Seq(
  "com.github.scala-incubator.io" %% "scala-io-core" % "0.4.2",
  "com.github.scala-incubator.io" %% "scala-io-file" % "0.4.2",
  "com.typesafe.akka" %% "akka-actor" % "2.2.4",
  "com.typesafe.akka" %% "akka-slf4j" % "2.2.4",
  "com.typesafe.scala-logging" %% "scala-logging-slf4j" % "2.1.2",
  "io.spray" % "spray-client" % "1.2.1",
  "jline" % "jline" % "2.12",
  "org.apache.curator" % "curator-recipes" % "2.4.2",
  "org.apache.zookeeper" % "zookeeper" % "3.4.6",
  "org.fusesource" % "sigar" % "1.6.4" classifier "native" classifier "",
  "org.mozilla" % "rhino" % "1.7R4",
  "org.reactivemongo" %% "play2-reactivemongo" % "0.10.2",
  "org.reactivemongo" %% "reactivemongo" % "0.10.0",
  "org.scalaz" %% "scalaz-core" % "7.0.6"
).map(_.exclude("org.slf4j", "slf4j-log4j12"))

val copyNativeLibraries = taskKey[Set[File]]("Copy native libraries to native libraries directory")

copyNativeLibraries := {
  val cp = (managedClasspath in Runtime).value
  // FIXME: Currently, only sigar has a native library.
  // Extract this as a setting when more native libraries are added.
  val nativeJarFile = cp.map(_.data).find(_.getName == "sigar-1.6.4-native.jar").get
  val nativeLibrariesDirectory = target.value / "native_libraries" / (System.getProperty("sun.arch.data.model") + "bits")
  IO.unzip(nativeJarFile, nativeLibrariesDirectory)
}

run <<= (run in Runtime) dependsOn copyNativeLibraries

(test in Test) <<= (test in Test) dependsOn copyNativeLibraries

val toolsJar = if (System.getProperty("os.name") != "Mac OS X") {
  Seq(Attributed.blank(file(System.getProperty("java.home").dropRight(3) + "lib/tools.jar")))
} else {
  Nil
}

// adding the tools.jar to the unmanaged-jars seq
unmanagedJars in Compile ~= (toolsJar ++ _)

lazy val root = (project in file(".")).enablePlugins(PlayScala)

org.scalastyle.sbt.ScalastylePlugin.Settings

scalariformSettings

Common.settings
