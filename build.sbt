import Dependencies._
import com.typesafe.tools.mima.core._, ProblemFilters._

def baseVersion = "0.1.0"

def commonSettings: Seq[Setting[_]] = Seq(
  scalaVersion := scala212,
  // publishArtifact in packageDoc := false,
  resolvers += Resolver.typesafeIvyRepo("releases"),
  resolvers += Resolver.sonatypeRepo("snapshots"),
  resolvers += "bintray-sbt-maven-releases" at "https://dl.bintray.com/sbt/maven-releases/",
  // concurrentRestrictions in Global += Util.testExclusiveRestriction,
  testOptions += Tests.Argument(TestFrameworks.ScalaCheck, "-w", "1"),
  javacOptions in compile ++= Seq("-Xlint", "-Xlint:-serial"),
  incOptions := incOptions.value.withNameHashing(true),
  crossScalaVersions := Seq(scala211, scala212),
  resolvers += Resolver.sonatypeRepo("public"),
  scalacOptions += "-Ywarn-unused",
  mimaPreviousArtifacts := Set(), // Some(organization.value %% moduleName.value % "1.0.0"),
  publishArtifact in Compile := true,
  publishArtifact in Test := false
)

lazy val lmRoot = (project in file(".")).
  aggregate(lm).
  settings(
    inThisBuild(Seq(
      homepage := Some(url("https://github.com/sbt/librarymanagement")),
      description := "Library management module for sbt",
      scmInfo := Some(ScmInfo(url("https://github.com/sbt/librarymanagement"), "git@github.com:sbt/librarymanagement.git")),
      bintrayPackage := "librarymanagement",
      git.baseVersion := baseVersion
    )),
    commonSettings,
    name := "LM Root",
    publish := {},
    publishLocal := {},
    publishArtifact in Compile := false,
    publishArtifact := false,
    customCommands
  )

lazy val lm = (project in file("librarymanagement")).
  settings(
    commonSettings,
    name := "librarymanagement",
    libraryDependencies ++= Seq(
      ivy, jsch, scalaReflect.value, launcherInterface, sjsonnewScalaJson % Optional),
    libraryDependencies ++= scalaXml.value,
    resourceGenerators in Compile += Def.task(Util.generateVersionFile(version.value, resourceManaged.value, streams.value, (compile in Compile).value)).taskValue,
    mimaBinaryIssueFilters ++= Seq(),
    contrabandFormatsForType in generateContrabands in Compile := DatatypeConfig.getFormats,
    // WORKAROUND sbt/sbt#2205 include managed sources in packageSrc
    mappings in (Compile, packageSrc) ++= {
      val srcs = (managedSources in Compile).value
      val sdirs = (managedSourceDirectories in Compile).value
      val base = baseDirectory.value
      (((srcs --- sdirs --- base) pair (relativeTo(sdirs) | relativeTo(base) | flat)) toSeq)
    }
  ).
  configure(addSbtIO, addSbtUtilLogging, addSbtUtilTesting, addSbtUtilCollection, addSbtUtilCompletion, addSbtUtilCache).
  enablePlugins(ContrabandPlugin, JsonCodecPlugin)

def customCommands: Seq[Setting[_]] = Seq(
  commands += Command.command("release") { state =>
    // "clean" ::
    "so compile" ::
    "so publishSigned" ::
    "reload" ::
    state
  }
)
