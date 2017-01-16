import Util._
import Dependencies._
import Sxr.sxr

import com.typesafe.tools.mima.core._, ProblemFilters._
import com.typesafe.tools.mima.plugin.MimaKeys.{ binaryIssueFilters, previousArtifact}
import com.typesafe.tools.mima.plugin.MimaPlugin.mimaDefaultSettings

// ThisBuild settings take lower precedence,
// but can be shared across the multi projects.
def buildLevelSettings: Seq[Setting[_]] = inThisBuild(Seq(
  organization := "org.scala-sbt",
  version := "1.0.0-SNAPSHOT",
  description := "sbt is an interactive build tool",
  bintrayOrganization := Some("sbt"),
  bintrayRepository := {
    if (!isSnapshot.value) "maven-releases"
    else "maven-snapshots"
  },
  bintrayPackage := "sbt",
  bintrayReleaseOnPublish := false,
  licenses := List("BSD New" -> url("https://github.com/sbt/sbt/blob/0.13/LICENSE")),
  developers := List(
    Developer("harrah", "Mark Harrah", "@harrah", url("https://github.com/harrah")),
    Developer("eed3si9n", "Eugene Yokota", "@eed3si9n", url("https://github.com/eed3si9n")),
    Developer("jsuereth", "Josh Suereth", "@jsuereth", url("https://github.com/jsuereth")),
    Developer("dwijnand", "Dale Wijnand", "@dwijnand", url("https://github.com/dwijnand")),
    Developer("gkossakowski", "Grzegorz Kossakowski", "@gkossakowski", url("https://github.com/gkossakowski")),
    Developer("Duhemm", "Martin Duhem", "@Duhemm", url("https://github.com/Duhemm"))
  ),
  homepage := Some(url("https://github.com/sbt/sbt")),
  scmInfo := Some(ScmInfo(url("https://github.com/sbt/sbt"), "git@github.com:sbt/sbt.git")),
  resolvers += Resolver.mavenLocal
))

def commonSettings: Seq[Setting[_]] = Seq[SettingsDefinition](
  scalaVersion := baseScalaVersion,
  componentID := None,
  resolvers += Resolver.typesafeIvyRepo("releases"),
  resolvers += Resolver.sonatypeRepo("snapshots"),
  resolvers += "bintray-sbt-maven-releases" at "https://dl.bintray.com/sbt/maven-releases/",
  concurrentRestrictions in Global += Util.testExclusiveRestriction,
  testOptions += Tests.Argument(TestFrameworks.ScalaCheck, "-w", "1"),
  javacOptions in compile ++= Seq("-target", "6", "-source", "6", "-Xlint", "-Xlint:-serial"),
  incOptions := incOptions.value.withNameHashing(true),
  crossScalaVersions := Seq(baseScalaVersion),
  bintrayPackage := (bintrayPackage in ThisBuild).value,
  bintrayRepository := (bintrayRepository in ThisBuild).value,
  mimaDefaultSettings,
  publishArtifact in Test := false,
  previousArtifact := None, // Some(organization.value % moduleName.value % "1.0.0"),
  binaryIssueFilters ++= Seq(
  )
) flatMap (_.settings)

def minimalSettings: Seq[Setting[_]] =
  commonSettings ++ customCommands ++
  publishPomSettings ++ Release.javaVersionCheckSettings

def baseSettings: Seq[Setting[_]] =
  minimalSettings ++ Seq(projectComponent) ++ baseScalacOptions ++ Licensed.settings ++ Formatting.settings

def testedBaseSettings: Seq[Setting[_]] =
  baseSettings ++ testDependencies

lazy val sbtRoot: Project = (project in file(".")).
  enablePlugins(ScriptedPlugin).
  configs(Sxr.sxrConf).
  aggregate(nonRoots: _*).
  settings(
    buildLevelSettings,
    minimalSettings,
    rootSettings,
    publish := {},
    publishLocal := {}
  )

// This is used to configure an sbt-launcher for this version of sbt.
lazy val bundledLauncherProj =
  (project in file("launch")).
  settings(
    minimalSettings,
    inConfig(Compile)(Transform.configSettings),
    Release.launcherSettings(sbtLaunchJar)
  ).
  enablePlugins(SbtLauncherPlugin).
  settings(
    name := "sbt-launch",
    moduleName := "sbt-launch",
    description := "sbt application launcher",
    autoScalaLibrary := false,
    crossPaths := false,
    publish := Release.deployLauncher.value,
    publishLauncher := Release.deployLauncher.value,
    packageBin in Compile := sbtLaunchJar.value
  )

/* ** subproject declarations ** */

/* **** Intermediate-level Modules **** */

// Runner for uniform test interface
lazy val testingProj = (project in file("testing")).
  dependsOn(testAgentProj).
  settings(
    baseSettings,
    name := "Testing",
    libraryDependencies ++= Seq(testInterface,launcherInterface)
  ).
  configure(addSbtIO, addSbtCompilerClasspath, addSbtUtilLogging)

// Testing agent for running tests in a separate process.
lazy val testAgentProj = (project in file("testing") / "agent").
  settings(
    minimalSettings,
    crossScalaVersions := Seq(baseScalaVersion),
    crossPaths := false,
    autoScalaLibrary := false,
    name := "Test Agent",
    libraryDependencies += testInterface
  )

// Basic task engine
lazy val taskProj = (project in file("tasks")).
  settings(
    testedBaseSettings,
    name := "Tasks"
  ).
  configure(addSbtUtilControl, addSbtUtilCollection)

// Standard task system.  This provides map, flatMap, join, and more on top of the basic task model.
lazy val stdTaskProj = (project in file("tasks-standard")).
  dependsOn (taskProj % "compile;test->test").
  settings(
    testedBaseSettings,
    name := "Task System",
    testExclusive
  ).
  configure(addSbtUtilCollection, addSbtUtilLogging, addSbtUtilCache, addSbtIO)

// Embedded Scala code runner
lazy val runProj = (project in file("run")).
  settings(
    testedBaseSettings,
    name := "Run"
  ).
  configure(addSbtIO, addSbtUtilLogging, addSbtCompilerClasspath)

lazy val scriptedSbtProj = (project in scriptedPath / "sbt").
  dependsOn(commandProj).
  settings(
    baseSettings,
    name := "Scripted sbt",
    libraryDependencies ++= Seq(launcherInterface % "provided")
  ).
  configure(addSbtIO, addSbtUtilLogging, addSbtCompilerInterface, addSbtUtilScripted)

lazy val scriptedPluginProj = (project in scriptedPath / "plugin").
  dependsOn(sbtProj).
  settings(
    baseSettings,
    name := "Scripted Plugin"
  ).
  configure(addSbtCompilerClasspath)

// Implementation and support code for defining actions.
lazy val actionsProj = (project in file("main-actions")).
  dependsOn(runProj, stdTaskProj, taskProj, testingProj).
  settings(
    testedBaseSettings,
    name := "Actions",
    libraryDependencies += sjsonNewScalaJson
  ).
  configure(addSbtCompilerClasspath, addSbtUtilCompletion, addSbtCompilerApiInfo,
    addSbtZinc, addSbtCompilerIvyIntegration, addSbtCompilerInterface,
    addSbtIO, addSbtUtilLogging, addSbtUtilRelation, addSbtLm, addSbtUtilTracking)

lazy val protocolProj = (project in file("protocol")).
  enablePlugins(ContrabandPlugin, JsonCodecPlugin).
  settings(
    testedBaseSettings,
    name := "Protocol",
    libraryDependencies ++= Seq(sjsonNewScalaJson),
    sourceManaged in (Compile, generateContrabands) := baseDirectory.value / "src" / "main" / "contraband-scala"
  ).
  configure(addSbtUtilLogging)

// General command support and core commands not specific to a build system
lazy val commandProj = (project in file("main-command")).
  enablePlugins(ContrabandPlugin, JsonCodecPlugin).
  dependsOn(protocolProj).
  settings(
    testedBaseSettings,
    name := "Command",
    libraryDependencies ++= Seq(launcherInterface, sjsonNewScalaJson, templateResolverApi),
    sourceManaged in (Compile, generateContrabands) := baseDirectory.value / "src" / "main" / "contraband-scala",
    contrabandFormatsForType in generateContrabands in Compile := ContrabandConfig.getFormats
  ).
  configure(addSbtCompilerInterface, addSbtIO, addSbtUtilLogging, addSbtUtilCompletion, addSbtCompilerClasspath, addSbtLm)

// Fixes scope=Scope for Setting (core defined in collectionProj) to define the settings system used in build definitions
lazy val mainSettingsProj = (project in file("main-settings")).
  dependsOn(commandProj, stdTaskProj).
  settings(
    testedBaseSettings,
    name := "Main Settings"
  ).
  configure(addSbtUtilCache, addSbtUtilApplyMacro, addSbtCompilerInterface, addSbtUtilRelation,
    addSbtUtilLogging, addSbtIO, addSbtUtilCompletion, addSbtCompilerClasspath, addSbtLm)

// The main integration project for sbt.  It brings all of the projects together, configures them, and provides for overriding conventions.
lazy val mainProj = (project in file("main")).
  dependsOn(actionsProj, mainSettingsProj, runProj, commandProj).
  settings(
    testedBaseSettings,
    name := "Main",
    libraryDependencies ++= scalaXml.value ++ Seq(launcherInterface)
  ).
  configure(addSbtCompilerInterface,
    addSbtIO, addSbtUtilLogging, addSbtUtilLogic, addSbtLm, addSbtZincCompile)

// Strictly for bringing implicits and aliases from subsystems into the top-level sbt namespace through a single package object
//  technically, we need a dependency on all of mainProj's dependencies, but we don't do that since this is strictly an integration project
//  with the sole purpose of providing certain identifiers without qualification (with a package object)
lazy val sbtProj = (project in file("sbt")).
  dependsOn(mainProj, scriptedSbtProj % "test->test").
  settings(
    baseSettings,
    name := "sbt",
    normalizedName := "sbt",
    crossScalaVersions := Seq(baseScalaVersion),
    crossPaths := false
  ).
  configure(addSbtCompilerBridge)

def scriptedTask: Def.Initialize[InputTask[Unit]] = Def.inputTask {
  val result = scriptedSource(dir => (s: State) => Scripted.scriptedParser(dir)).parsed
  publishLocalBinAll.value
  // These two projects need to be visible in a repo even if the default
  // local repository is hidden, so we publish them to an alternate location and add
  // that alternate repo to the running scripted test (in Scripted.scriptedpreScripted).
  // (altLocalPublish in interfaceProj).value
  // (altLocalPublish in compileInterfaceProj).value
  Scripted.doScripted((sbtLaunchJar in bundledLauncherProj).value, (fullClasspath in scriptedSbtProj in Test).value,
    (scalaInstance in scriptedSbtProj).value,
    scriptedSource.value, scriptedBufferLog.value, result, scriptedPrescripted.value, scriptedLaunchOpts.value)
}

def scriptedUnpublishedTask: Def.Initialize[InputTask[Unit]] = Def.inputTask {
  val result = scriptedSource(dir => (s: State) => Scripted.scriptedParser(dir)).parsed
  Scripted.doScripted((sbtLaunchJar in bundledLauncherProj).value, (fullClasspath in scriptedSbtProj in Test).value,
    (scalaInstance in scriptedSbtProj).value,
    scriptedSource.value, scriptedBufferLog.value, result, scriptedPrescripted.value, scriptedLaunchOpts.value)
}

lazy val publishLauncher = TaskKey[Unit]("publish-launcher")

lazy val myProvided = config("provided") intransitive

def allProjects = Seq(
  testingProj, testAgentProj, taskProj, stdTaskProj, runProj,
  scriptedSbtProj, scriptedPluginProj, protocolProj,
  actionsProj, commandProj, mainSettingsProj, mainProj, sbtProj, bundledLauncherProj)

def projectsWithMyProvided = allProjects.map(p => p.copy(configurations = (p.configurations.filter(_ != Provided)) :+ myProvided))
lazy val nonRoots = projectsWithMyProvided.map(p => LocalProject(p.id))

def rootSettings = fullDocSettings ++
  Util.publishPomSettings ++ otherRootSettings ++ Formatting.sbtFilesSettings ++
  Transform.conscriptSettings(bundledLauncherProj)
def otherRootSettings = Seq(
  scripted <<= scriptedTask,
  scriptedUnpublished <<= scriptedUnpublishedTask,
  scriptedSource := (sourceDirectory in sbtProj).value / "sbt-test",
  // scriptedPrescripted := { addSbtAlternateResolver _ },
  scriptedLaunchOpts := List("-XX:MaxPermSize=256M", "-Xmx1G"),
  publishAll := { val _ = (publishLocal).all(ScopeFilter(inAnyProject)).value },
  publishLocalBinAll := { val _ = (publishLocalBin).all(ScopeFilter(inAnyProject)).value },
  aggregate in bintrayRelease := false
) ++ inConfig(Scripted.RepoOverrideTest)(Seq(
  scriptedPrescripted := { _ => () },
  scriptedLaunchOpts := {
    List("-XX:MaxPermSize=256M", "-Xmx1G", "-Dsbt.override.build.repos=true",
      s"""-Dsbt.repository.config=${ scriptedSource.value / "repo.config" }""")
  },
  scripted <<= scriptedTask,
  scriptedUnpublished <<= scriptedUnpublishedTask,
  scriptedSource := (sourceDirectory in sbtProj).value / "repo-override-test"
))

// def addSbtAlternateResolver(scriptedRoot: File) = {
//   val resolver = scriptedRoot / "project" / "AddResolverPlugin.scala"
//   if (!resolver.exists) {
//     IO.write(resolver, s"""import sbt._
//                           |import Keys._
//                           |
//                           |object AddResolverPlugin extends AutoPlugin {
//                           |  override def requires = sbt.plugins.JvmPlugin
//                           |  override def trigger = allRequirements
//                           |
//                           |  override lazy val projectSettings = Seq(resolvers += alternativeLocalResolver)
//                           |  lazy val alternativeLocalResolver = Resolver.file("$altLocalRepoName", file("$altLocalRepoPath"))(Resolver.ivyStylePatterns)
//                           |}
//                           |""".stripMargin)
//   }
// }

lazy val docProjects: ScopeFilter = ScopeFilter(
  inAnyProject -- inProjects(sbtRoot, sbtProj, scriptedSbtProj, scriptedPluginProj),
  inConfigurations(Compile)
)
def fullDocSettings = Util.baseScalacOptions ++ Docs.settings ++ Sxr.settings ++ Seq(
  scalacOptions += "-Ymacro-no-expand", // for both sxr and doc
  sources in sxr := {
    val allSources = (sources ?? Nil).all(docProjects).value
    allSources.flatten.distinct
  }, //sxr
  sources in (Compile, doc) := (sources in sxr).value, // doc
  Sxr.sourceDirectories := {
    val allSourceDirectories = (sourceDirectories ?? Nil).all(docProjects).value
    allSourceDirectories.flatten
  },
  fullClasspath in sxr := (externalDependencyClasspath in Compile in sbtProj).value,
  dependencyClasspath in (Compile, doc) := (fullClasspath in sxr).value
)

lazy val safeUnitTests = taskKey[Unit]("Known working tests (for both 2.10 and 2.11)")
lazy val safeProjects: ScopeFilter = ScopeFilter(
  inProjects(mainSettingsProj, mainProj,
    actionsProj, runProj, stdTaskProj),
  inConfigurations(Test)
)
lazy val otherUnitTests = taskKey[Unit]("Unit test other projects")
lazy val otherProjects: ScopeFilter = ScopeFilter(
  inProjects(
    testingProj, testAgentProj, taskProj,
    scriptedSbtProj, scriptedPluginProj,
    commandProj, mainSettingsProj, mainProj,
    sbtProj),
  inConfigurations(Test)
)

def customCommands: Seq[Setting[_]] = Seq(
  commands += Command.command("setupBuildScala212") { state =>
    s"""set scalaVersion in ThisBuild := "$scala212" """ ::
      state
  },
  safeUnitTests := {
    test.all(safeProjects).value
  },
  otherUnitTests := {
    test.all(otherProjects).value
  },
  commands += Command.command("release-sbt-local") { state =>
    "clean" ::
    "so compile" ::
    "so publishLocal" ::
    "reload" ::
    state
  },
  /** There are several complications with sbt's build.
   * First is the fact that interface project is a Java-only project
   * that uses source generator from datatype subproject in Scala 2.10.6.
   *
   * Second is the fact that all subprojects are released with crossPaths
   * turned off for the sbt's Scala version 2.10.6, but some of them are also
   * cross published against 2.11.1 with crossPaths turned on.
   *
   * `so compile` handles 2.10.x/2.11.x cross building.
   */
  commands += Command.command("release-sbt") { state =>
    // TODO - Any sort of validation
    "clean" ::
      "conscript-configs" ::
      "so compile" ::
      "so publishSigned" ::
      "bundledLauncherProj/publishLauncher" ::
      state
  },
  // stamp-version doesn't work with ++ or "so".
  commands += Command.command("release-nightly") { state =>
    "stamp-version" ::
      "clean" ::
      "compile" ::
      "publish" ::
      "bintrayRelease" ::
      state
  }
)
