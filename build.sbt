import Util._
import Dependencies._
import Sxr.sxr
import com.typesafe.tools.mima.core._, ProblemFilters._

// ThisBuild settings take lower precedence,
// but can be shared across the multi projects.
def buildLevelSettings: Seq[Setting[_]] =
  inThisBuild(
    Seq(
      organization := "org.scala-sbt",
      version := "1.0.3-SNAPSHOT",
      description := "sbt is an interactive build tool",
      bintrayOrganization := Some("sbt"),
      bintrayRepository := {
        if (publishStatus.value == "releases") "maven-releases"
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
        Developer("gkossakowski",
                  "Grzegorz Kossakowski",
                  "@gkossakowski",
                  url("https://github.com/gkossakowski")),
        Developer("Duhemm", "Martin Duhem", "@Duhemm", url("https://github.com/Duhemm"))
      ),
      homepage := Some(url("https://github.com/sbt/sbt")),
      scmInfo := Some(ScmInfo(url("https://github.com/sbt/sbt"), "git@github.com:sbt/sbt.git")),
      resolvers += Resolver.mavenLocal,
      scalafmtOnCompile := true,
      scalafmtVersion := "1.3.0",
    ))

def commonSettings: Seq[Setting[_]] =
  Seq[SettingsDefinition](
    headerLicense := Some(HeaderLicense.Custom(
      """|sbt
         |Copyright 2011 - 2017, Lightbend, Inc.
         |Copyright 2008 - 2010, Mark Harrah
         |Licensed under BSD-3-Clause license (see LICENSE)
         |""".stripMargin
    )),
    scalaVersion := baseScalaVersion,
    componentID := None,
    resolvers += Resolver.typesafeIvyRepo("releases"),
    resolvers += Resolver.sonatypeRepo("snapshots"),
    resolvers += "bintray-sbt-maven-releases" at "https://dl.bintray.com/sbt/maven-releases/",
    addCompilerPlugin("org.spire-math" % "kind-projector" % "0.9.4" cross CrossVersion.binary),
    concurrentRestrictions in Global += Util.testExclusiveRestriction,
    testOptions in Test += Tests.Argument(TestFrameworks.ScalaCheck, "-w", "1"),
    testOptions in Test += Tests.Argument(TestFrameworks.ScalaCheck, "-verbosity", "2"),
    javacOptions in compile ++= Seq("-target", "6", "-source", "6", "-Xlint", "-Xlint:-serial"),
    crossScalaVersions := Seq(baseScalaVersion),
    bintrayPackage := (bintrayPackage in ThisBuild).value,
    bintrayRepository := (bintrayRepository in ThisBuild).value,
    publishArtifact in Test := false,
    fork in compile := true,
    fork in run := true
  ) flatMap (_.settings)

def minimalSettings: Seq[Setting[_]] =
  commonSettings ++ customCommands ++
    publishPomSettings ++ Release.javaVersionCheckSettings

def baseSettings: Seq[Setting[_]] =
  minimalSettings ++ Seq(projectComponent) ++ baseScalacOptions ++ Licensed.settings

def testedBaseSettings: Seq[Setting[_]] =
  baseSettings ++ testDependencies

val mimaSettings = Def settings (
  mimaPreviousArtifacts := Set(
    organization.value % moduleName.value % "1.0.0"
      cross (if (crossPaths.value) CrossVersion.binary else CrossVersion.disabled)
  )
)

lazy val sbtRoot: Project = (project in file("."))
  .enablePlugins(ScriptedPlugin) // , SiteScaladocPlugin, GhpagesPlugin)
  .configs(Sxr.SxrConf)
  .aggregate(nonRoots: _*)
  .settings(
    buildLevelSettings,
    minimalSettings,
    Util.baseScalacOptions,
    Docs.settings,
    Sxr.settings,
    scalacOptions += "-Ymacro-expand:none", // for both sxr and doc
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
    dependencyClasspath in (Compile, doc) := (fullClasspath in sxr).value,
    Util.publishPomSettings,
    otherRootSettings,
    Transform.conscriptSettings(bundledLauncherProj),
    publish := {},
    publishLocal := {},
    skip in publish := true
  )

// This is used to configure an sbt-launcher for this version of sbt.
lazy val bundledLauncherProj =
  (project in file("launch"))
    .settings(
      minimalSettings,
      inConfig(Compile)(Transform.configSettings),
      Release.launcherSettings(sbtLaunchJar)
    )
    .enablePlugins(SbtLauncherPlugin)
    .settings(
      name := "sbt-launch",
      moduleName := "sbt-launch",
      description := "sbt application launcher",
      autoScalaLibrary := false,
      crossPaths := false,
      // mimaSettings, // TODO: Configure MiMa, deal with Proguard
      publish := Release.deployLauncher.value,
      publishLauncher := Release.deployLauncher.value,
      packageBin in Compile := sbtLaunchJar.value
    )

/* ** subproject declarations ** */

val collectionProj = (project in file("internal") / "util-collection")
  .settings(
    testedBaseSettings,
    Util.keywordsSettings,
    name := "Collections",
    libraryDependencies ++= Seq(sjsonNewScalaJson.value),
    mimaSettings,
    mimaBinaryIssueFilters ++= Seq(
      // Added private[sbt] method to capture State attributes.
      exclude[ReversedMissingMethodProblem]("sbt.internal.util.AttributeMap.setCond"),

      // Dropped in favour of kind-projector's inline type lambda syntax
      exclude[MissingClassProblem]("sbt.internal.util.TypeFunctions$P1of2"),

      // Dropped in favour of kind-projector's polymorphic lambda literals
      exclude[MissingClassProblem]("sbt.internal.util.Param"),
      exclude[MissingClassProblem]("sbt.internal.util.Param$"),

      // Dropped in favour of plain scala.Function, and its compose method
      exclude[MissingClassProblem]("sbt.internal.util.Fn1"),
      exclude[DirectMissingMethodProblem]("sbt.internal.util.TypeFunctions.toFn1"),
      exclude[DirectMissingMethodProblem]("sbt.internal.util.Types.toFn1"),
    ),
  )
  .configure(addSbtUtilPosition)

// Command line-related utilities.
val completeProj = (project in file("internal") / "util-complete")
  .dependsOn(collectionProj)
  .settings(
    testedBaseSettings,
    name := "Completion",
    libraryDependencies += jline,
    mimaSettings,
  )
  .configure(addSbtIO, addSbtUtilControl)

// A logic with restricted negation as failure for a unique, stable model
val logicProj = (project in file("internal") / "util-logic")
  .dependsOn(collectionProj)
  .settings(
    testedBaseSettings,
    name := "Logic",
    mimaSettings,
  )
  .configure(addSbtUtilRelation)

/* **** Intermediate-level Modules **** */

// Runner for uniform test interface
lazy val testingProj = (project in file("testing"))
  .enablePlugins(ContrabandPlugin, JsonCodecPlugin)
  .dependsOn(testAgentProj)
  .settings(
    baseSettings,
    name := "Testing",
    libraryDependencies ++= Seq(testInterface, launcherInterface, sjsonNewScalaJson.value),
    managedSourceDirectories in Compile +=
      baseDirectory.value / "src" / "main" / "contraband-scala",
    sourceManaged in (Compile, generateContrabands) := baseDirectory.value / "src" / "main" / "contraband-scala",
    contrabandFormatsForType in generateContrabands in Compile := ContrabandConfig.getFormats,
    mimaSettings,
  )
  .configure(addSbtIO, addSbtCompilerClasspath, addSbtUtilLogging)

// Testing agent for running tests in a separate process.
lazy val testAgentProj = (project in file("testing") / "agent")
  .settings(
    minimalSettings,
    crossScalaVersions := Seq(baseScalaVersion),
    crossPaths := false,
    autoScalaLibrary := false,
    name := "Test Agent",
    libraryDependencies += testInterface,
    mimaSettings,
  )

// Basic task engine
lazy val taskProj = (project in file("tasks"))
  .dependsOn(collectionProj)
  .settings(
    testedBaseSettings,
    name := "Tasks",
    mimaSettings,
  )
  .configure(addSbtUtilControl)

// Standard task system.  This provides map, flatMap, join, and more on top of the basic task model.
lazy val stdTaskProj = (project in file("tasks-standard"))
  .dependsOn(collectionProj)
  .dependsOn(taskProj % "compile;test->test")
  .settings(
    testedBaseSettings,
    name := "Task System",
    testExclusive,
    mimaSettings,
  )
  .configure(addSbtIO, addSbtUtilLogging, addSbtUtilCache)

// Embedded Scala code runner
lazy val runProj = (project in file("run"))
  .enablePlugins(ContrabandPlugin)
  .dependsOn(collectionProj)
  .settings(
    testedBaseSettings,
    name := "Run",
    managedSourceDirectories in Compile +=
      baseDirectory.value / "src" / "main" / "contraband-scala",
    sourceManaged in (Compile, generateContrabands) := baseDirectory.value / "src" / "main" / "contraband-scala",
    mimaSettings,
  )
  .configure(addSbtIO, addSbtUtilLogging, addSbtCompilerClasspath)

lazy val scriptedSbtProj = (project in scriptedPath / "sbt")
  .dependsOn(commandProj)
  .settings(
    baseSettings,
    name := "Scripted sbt",
    libraryDependencies ++= Seq(launcherInterface % "provided"),
    mimaSettings,
  )
  .configure(addSbtIO, addSbtUtilLogging, addSbtCompilerInterface, addSbtUtilScripted, addSbtLmCore)

lazy val scriptedPluginProj = (project in scriptedPath / "plugin")
  .dependsOn(sbtProj)
  .settings(
    baseSettings,
    name := "Scripted Plugin",
    mimaSettings,
  )
  .configure(addSbtCompilerClasspath)

// Implementation and support code for defining actions.
lazy val actionsProj = (project in file("main-actions"))
  .dependsOn(completeProj, runProj, stdTaskProj, taskProj, testingProj)
  .settings(
    testedBaseSettings,
    name := "Actions",
    libraryDependencies += sjsonNewScalaJson.value,
    mimaSettings,
  )
  .configure(
    addSbtIO,
    addSbtUtilLogging,
    addSbtUtilRelation,
    addSbtUtilTracking,
    addSbtCompilerInterface,
    addSbtCompilerClasspath,
    addSbtCompilerApiInfo,
    addSbtLmCore,
    addSbtCompilerIvyIntegration,
    addSbtZinc
  )

lazy val protocolProj = (project in file("protocol"))
  .enablePlugins(ContrabandPlugin, JsonCodecPlugin)
  .settings(
    testedBaseSettings,
    name := "Protocol",
    libraryDependencies ++= Seq(sjsonNewScalaJson.value),
    managedSourceDirectories in Compile +=
      baseDirectory.value / "src" / "main" / "contraband-scala",
    sourceManaged in (Compile, generateContrabands) := baseDirectory.value / "src" / "main" / "contraband-scala",
    contrabandFormatsForType in generateContrabands in Compile := ContrabandConfig.getFormats,
    mimaSettings,
  )
  .configure(addSbtUtilLogging)

// General command support and core commands not specific to a build system
lazy val commandProj = (project in file("main-command"))
  .enablePlugins(ContrabandPlugin, JsonCodecPlugin)
  .dependsOn(protocolProj, completeProj)
  .settings(
    testedBaseSettings,
    name := "Command",
    libraryDependencies ++= Seq(launcherInterface, sjsonNewScalaJson.value, templateResolverApi),
    managedSourceDirectories in Compile +=
      baseDirectory.value / "src" / "main" / "contraband-scala",
    sourceManaged in (Compile, generateContrabands) := baseDirectory.value / "src" / "main" / "contraband-scala",
    contrabandFormatsForType in generateContrabands in Compile := ContrabandConfig.getFormats,
    mimaSettings,
    mimaBinaryIssueFilters ++= Vector(
      // Changed the signature of Server method. nacho cheese.
      exclude[DirectMissingMethodProblem]("sbt.internal.server.Server.*"),
      // Added method to ServerInstance. This is also internal.
      exclude[ReversedMissingMethodProblem]("sbt.internal.server.ServerInstance.*"),
      // Added method to CommandChannel. internal.
      exclude[ReversedMissingMethodProblem]("sbt.internal.CommandChannel.*"),
      // Added an overload to reboot. The overload is private[sbt].
      exclude[ReversedMissingMethodProblem]("sbt.StateOps.reboot"),
    )
  )
  .configure(
    addSbtIO,
    addSbtUtilLogging,
    addSbtCompilerInterface,
    addSbtCompilerClasspath,
    addSbtLmCore
  )

// The core macro project defines the main logic of the DSL, abstracted
// away from several sbt implementators (tasks, settings, et cetera).
lazy val coreMacrosProj = (project in file("core-macros"))
  .dependsOn(collectionProj)
  .settings(
    commonSettings,
    name := "Core Macros",
    libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value,
    mimaSettings,
  )

/* Write all the compile-time dependencies of the spores macro to a file,
 * in order to read it from the created Toolbox to run the neg tests. */
lazy val generateToolboxClasspath = Def.task {
  val classpathAttributes = (dependencyClasspath in Compile).value
  val dependenciesClasspath =
    classpathAttributes.map(_.data.getAbsolutePath).mkString(":")
  val scalaBinVersion = (scalaBinaryVersion in Compile).value
  val targetDir = (target in Compile).value
  val compiledClassesDir = targetDir / s"scala-$scalaBinVersion/classes"
  val testClassesDir = targetDir / s"scala-$scalaBinVersion/test-classes"
  val classpath = s"$compiledClassesDir:$testClassesDir:$dependenciesClasspath"
  val resourceDir = (resourceDirectory in Compile).value
  resourceDir.mkdir() // In case it doesn't exist
  val toolboxTestClasspath = resourceDir / "toolbox.classpath"
  IO.write(toolboxTestClasspath, classpath)
  val result = List(toolboxTestClasspath.getAbsoluteFile)
  streams.value.log.success("Wrote the classpath for the macro neg test suite.")
  result
}

// Fixes scope=Scope for Setting (core defined in collectionProj) to define the settings system used in build definitions
lazy val mainSettingsProj = (project in file("main-settings"))
  .dependsOn(completeProj, commandProj, stdTaskProj, coreMacrosProj)
  .settings(
    testedBaseSettings,
    name := "Main Settings",
    resourceGenerators in Compile += generateToolboxClasspath.taskValue,
    mimaSettings,
  )
  .configure(
    addSbtIO,
    addSbtUtilLogging,
    addSbtUtilCache,
    addSbtUtilRelation,
    addSbtCompilerInterface,
    addSbtCompilerClasspath,
    addSbtLmCore
  )

// The main integration project for sbt.  It brings all of the projects together, configures them, and provides for overriding conventions.
lazy val mainProj = (project in file("main"))
  .enablePlugins(ContrabandPlugin)
  .dependsOn(logicProj, actionsProj, mainSettingsProj, runProj, commandProj, collectionProj)
  .settings(
    testedBaseSettings,
    name := "Main",
    libraryDependencies ++= scalaXml.value ++ Seq(launcherInterface) ++ log4jDependencies ++ Seq(scalaCacheCaffeine),
    managedSourceDirectories in Compile +=
      baseDirectory.value / "src" / "main" / "contraband-scala",
    sourceManaged in (Compile, generateContrabands) := baseDirectory.value / "src" / "main" / "contraband-scala",
    mimaSettings,
    mimaBinaryIssueFilters ++= Vector(
      // Changed the signature of NetworkChannel ctor. internal.
      exclude[DirectMissingMethodProblem]("sbt.internal.server.NetworkChannel.*"),
      // ctor for ConfigIndex. internal.
      exclude[DirectMissingMethodProblem]("sbt.internal.ConfigIndex.*"),
      // New and changed methods on KeyIndex. internal.
      exclude[ReversedMissingMethodProblem]("sbt.internal.KeyIndex.*"),
      exclude[DirectMissingMethodProblem]("sbt.internal.KeyIndex.*"),
      // Removed unused val. internal.
      exclude[DirectMissingMethodProblem]("sbt.internal.RelayAppender.jsonFormat"),
      // Removed unused def. internal.
      exclude[DirectMissingMethodProblem]("sbt.internal.Load.isProjectThis"),
    )
  )
  .configure(
    addSbtIO,
    addSbtUtilLogging,
    addSbtLmCore,
    addSbtLmIvy,
    addSbtCompilerInterface,
    addSbtZincCompile
  )

// Strictly for bringing implicits and aliases from subsystems into the top-level sbt namespace through a single package object
//  technically, we need a dependency on all of mainProj's dependencies, but we don't do that since this is strictly an integration project
//  with the sole purpose of providing certain identifiers without qualification (with a package object)
lazy val sbtProj = (project in file("sbt"))
  .dependsOn(mainProj, scriptedSbtProj % "test->test")
  .settings(
    baseSettings,
    name := "sbt",
    normalizedName := "sbt",
    crossScalaVersions := Seq(baseScalaVersion),
    crossPaths := false,
    mimaSettings,
    mimaBinaryIssueFilters ++= Vector(
      // Added more items to Import trait.
      exclude[ReversedMissingMethodProblem]("sbt.Import.sbt$Import$_setter_$WatchSource_="),
      exclude[ReversedMissingMethodProblem]("sbt.Import.WatchSource"),

      // Dropped in favour of kind-projector's polymorphic lambda literals
      exclude[DirectMissingMethodProblem]("sbt.Import.Param"),
      exclude[DirectMissingMethodProblem]("sbt.package.Param"),

      // Dropped in favour of plain scala.Function, and its compose method
      exclude[DirectMissingMethodProblem]("sbt.package.toFn1"),
    )
  )
  .configure(addSbtCompilerBridge)

def runNpm(command: String, base: File, log: sbt.internal.util.ManagedLogger) = {
  val npm = if (sbt.internal.util.Util.isWindows) "npm.cmd" else "npm"
  import scala.sys.process._
  try {
    val exitCode = Process(s"$npm $command", Option(base)) ! log
    if (exitCode != 0) throw new Exception("Process returned exit code: " + exitCode)
  } catch {
    case e: java.io.IOException => log.warn("failed to run npm " + e.getMessage)
  }
}

lazy val vscodePlugin = (project in file("vscode-sbt-scala"))
  .settings(
    crossPaths := false,
    crossScalaVersions := Seq(baseScalaVersion),
    skip in publish := true,
    compile in Compile := {
      val u = update.value
      runNpm("run compile", baseDirectory.value, streams.value.log)
      sbt.internal.inc.Analysis.empty
    },
    update := {
      val old = update.value
      val t = target.value / "updated"
      val base = baseDirectory.value
      val log = streams.value.log
      if (t.exists) ()
      else {
        runNpm("install", base, log)
        IO.touch(t)
      }
      old
    },
    cleanFiles ++= {
      val base = baseDirectory.value
      Vector(
        target.value / "updated",
        base / "node_modules", base / "client" / "node_modules",
        base / "client" / "server",
        base / "client" / "out",
        base / "server" / "node_modules") filter { _.exists }
    }
  )

def scriptedTask: Def.Initialize[InputTask[Unit]] = Def.inputTask {
  val result = scriptedSource(dir => (s: State) => Scripted.scriptedParser(dir)).parsed
  // publishLocalBinAll.value // TODO: Restore scripted needing only binary jars.
  publishAll.value
  // These two projects need to be visible in a repo even if the default
  // local repository is hidden, so we publish them to an alternate location and add
  // that alternate repo to the running scripted test (in Scripted.scriptedpreScripted).
  // (altLocalPublish in interfaceProj).value
  // (altLocalPublish in compileInterfaceProj).value
  Scripted.doScripted(
    (sbtLaunchJar in bundledLauncherProj).value,
    (fullClasspath in scriptedSbtProj in Test).value,
    (scalaInstance in scriptedSbtProj).value,
    scriptedSource.value,
    scriptedBufferLog.value,
    result,
    scriptedPrescripted.value,
    scriptedLaunchOpts.value
  )
}

def scriptedUnpublishedTask: Def.Initialize[InputTask[Unit]] = Def.inputTask {
  val result = scriptedSource(dir => (s: State) => Scripted.scriptedParser(dir)).parsed
  Scripted.doScripted(
    (sbtLaunchJar in bundledLauncherProj).value,
    (fullClasspath in scriptedSbtProj in Test).value,
    (scalaInstance in scriptedSbtProj).value,
    scriptedSource.value,
    scriptedBufferLog.value,
    result,
    scriptedPrescripted.value,
    scriptedLaunchOpts.value
  )
}

lazy val publishLauncher = TaskKey[Unit]("publish-launcher")

def allProjects =
  Seq(
    collectionProj,
    logicProj,
    completeProj,
    testingProj,
    testAgentProj,
    taskProj,
    stdTaskProj,
    runProj,
    scriptedSbtProj,
    scriptedPluginProj,
    protocolProj,
    actionsProj,
    commandProj,
    mainSettingsProj,
    mainProj,
    sbtProj,
    bundledLauncherProj,
    coreMacrosProj
  )

lazy val nonRoots = allProjects.map(p => LocalProject(p.id))

def otherRootSettings =
  Seq(
    scripted := scriptedTask.evaluated,
    scriptedUnpublished := scriptedUnpublishedTask.evaluated,
    scriptedSource := (sourceDirectory in sbtProj).value / "sbt-test",
    // scriptedPrescripted := { addSbtAlternateResolver _ },
    scriptedLaunchOpts := List("-Xmx1500M", "-Xms512M", "-server"),
    publishAll := { val _ = (publishLocal).all(ScopeFilter(inAnyProject)).value },
    publishLocalBinAll := { val _ = (publishLocalBin).all(ScopeFilter(inAnyProject)).value },
    aggregate in bintrayRelease := false
  ) ++ inConfig(Scripted.RepoOverrideTest)(
    Seq(
      scriptedPrescripted := (_ => ()),
      scriptedLaunchOpts := List(
        "-Xmx1500M",
        "-Xms512M",
        "-server",
        "-Dsbt.override.build.repos=true",
        s"""-Dsbt.repository.config=${scriptedSource.value / "repo.config"}"""
      ),
      scripted := scriptedTask.evaluated,
      scriptedUnpublished := scriptedUnpublishedTask.evaluated,
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
lazy val safeUnitTests = taskKey[Unit]("Known working tests (for both 2.10 and 2.11)")
lazy val safeProjects: ScopeFilter = ScopeFilter(
  inProjects(mainSettingsProj, mainProj, actionsProj, runProj, stdTaskProj),
  inConfigurations(Test)
)
lazy val otherUnitTests = taskKey[Unit]("Unit test other projects")
lazy val otherProjects: ScopeFilter = ScopeFilter(
  inProjects(
    testingProj,
    testAgentProj,
    taskProj,
    scriptedSbtProj,
    scriptedPluginProj,
    commandProj,
    mainSettingsProj,
    mainProj,
    sbtProj
  ),
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
      "conscriptConfigs" ::
      "compile" ::
      "publishSigned" ::
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
