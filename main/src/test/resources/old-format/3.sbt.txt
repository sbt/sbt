parallelExecution in Global := false

val commonSettings = Seq(
  organization := "com.github.germanosin",
  version := "1.0",
  javacOptions in Compile ++= Seq("-source", "1.6", "-target", "1.6"),
  scalaVersion := "2.11.1"
)

lazy val foldermessages = project.settings(commonSettings: _*)
  .settings(
    name := "play-foldermessages",
    crossScalaVersions := Seq("2.10.4", "2.11.1"),
    libraryDependencies += "com.typesafe.play" %% "play" % "2.3.2",
    resolvers += "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/",
    publishMavenStyle := true,
    publishTo := {
      val nexus = "https://oss.sonatype.org"
      if (isSnapshot.value) Some("snapshots" at s"$nexus/content/repositories/snapshots")
      else Some("releases" at s"$nexus/service/local/staging/deploy/maven2")
    },
    pomExtra := (
      <url>http://github.com/germanosin/play-foldermessages</url>
      <licenses>
        <license>
          <name>MIT License</name>
          <url>http://opensource.org/licenses/mit-license.php</url>
        </license>
      </licenses>
      <scm>
        <url>git@github.com:germanosin/play-foldermessages.git</url>
        <connection>scm:git:git@github.com:germanosin/play-foldermessages.git</connection>
      </scm>
      <developers>
        <developer>
          <id>germanosin</id>
          <name>German Osin</name>
        </developer>
      </developers>
    ),
    useGpg := true
  )

lazy val sampleApp = Project("sample-app", file("sample-app"))
  .settings(commonSettings: _*)
  .enablePlugins(PlayScala)
  .dependsOn(foldermessages)

lazy val playFolderMessages = project.in(file("."))
  .settings(commonSettings: _*)
  .aggregate(foldermessages,sampleApp)  