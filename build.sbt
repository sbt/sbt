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
      version := "1.2.0-SNAPSHOT",
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
        Developer(
          "gkossakowski",
          "Grzegorz Kossakowski",
          "@gkossakowski",
          url("https://github.com/gkossakowski")
        ),
        Developer("Duhemm", "Martin Duhem", "@Duhemm", url("https://github.com/Duhemm"))
      ),
      homepage := Some(url("https://github.com/sbt/sbt")),
      scmInfo := Some(ScmInfo(url("https://github.com/sbt/sbt"), "git@github.com:sbt/sbt.git")),
      resolvers += Resolver.mavenLocal,
      scalafmtOnCompile := true,
      scalafmtOnCompile in Sbt := false,
      scalafmtVersion := "1.3.0",
    ))

def commonSettings: Seq[Setting[_]] = Def.settings(
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
  javacOptions in compile ++= Seq("-Xlint", "-Xlint:-serial"),
  crossScalaVersions := Seq(baseScalaVersion),
  bintrayPackage := (bintrayPackage in ThisBuild).value,
  bintrayRepository := (bintrayRepository in ThisBuild).value,
  publishArtifact in Test := false,
  fork in compile := true,
  fork in run := true
)

def minimalSettings: Seq[Setting[_]] =
  commonSettings ++ customCommands ++
    publishPomSettings ++ Release.javaVersionCheckSettings

def baseSettings: Seq[Setting[_]] =
  minimalSettings ++ Seq(projectComponent) ++ baseScalacOptions ++ Licensed.settings

def testedBaseSettings: Seq[Setting[_]] =
  baseSettings ++ testDependencies

val mimaSettings = Def settings (
  mimaPreviousArtifacts := {
    Seq(
      "1.0.0", "1.0.1", "1.0.2", "1.0.3", "1.0.4",
      "1.1.0", "1.1.1",
    ).map { v =>
      organization.value % moduleName.value % v cross (if (crossPaths.value) CrossVersion.binary else CrossVersion.disabled)
    }.toSet
  },
  mimaBinaryIssueFilters ++= Seq(
    // Changes in the internal pacakge
    exclude[DirectMissingMethodProblem]("sbt.internal.*"),
    exclude[FinalClassProblem]("sbt.internal.*"),
    exclude[FinalMethodProblem]("sbt.internal.*"),
    exclude[IncompatibleResultTypeProblem]("sbt.internal.*"),
  ),
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
    skip in publish := true,
    commands in Global += Command.single("sbtOn")((state, dir) =>
      s"sbtProj/test:runMain sbt.RunFromSourceMain $dir" :: state),
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

      // Instead of defining foldr in KList & overriding in KCons,
      // it's now abstract in KList and defined in both KCons & KNil.
      exclude[FinalMethodProblem]("sbt.internal.util.KNil.foldr"),
      exclude[DirectAbstractMethodProblem]("sbt.internal.util.KList.foldr"),
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
    mimaBinaryIssueFilters ++= Seq(
    ),
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
    mimaBinaryIssueFilters ++= Seq(
      // private[sbt]
      ProblemFilters.exclude[IncompatibleMethTypeProblem]("sbt.TestStatus.write"),
      ProblemFilters.exclude[IncompatibleResultTypeProblem]("sbt.TestStatus.read"),
    ),
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

val sbtProjDepsCompileScopeFilter =
  ScopeFilter(inDependencies(LocalProject("sbtProj"), includeRoot = false), inConfigurations(Compile))

lazy val scriptedSbtProj = (project in scriptedPath / "sbt")
  .dependsOn(commandProj)
  .settings(
    baseSettings,
    name := "Scripted sbt",
    libraryDependencies ++= Seq(launcherInterface % "provided"),
    resourceGenerators in Compile += Def task {
      val mainClassDir = (classDirectory in Compile in LocalProject("sbtProj")).value
      val testClassDir = (classDirectory in Test in LocalProject("sbtProj")).value
      val classDirs = (classDirectory all sbtProjDepsCompileScopeFilter).value
      val extDepsCp = (externalDependencyClasspath in Compile in LocalProject("sbtProj")).value

      val cpStrings = (mainClassDir +: testClassDir +: classDirs) ++ extDepsCp.files map (_.toString)

      val file = (resourceManaged in Compile).value / "RunFromSource.classpath"
      IO.writeLines(file, cpStrings)
      List(file)
    },
    mimaSettings,
    mimaBinaryIssueFilters ++= Seq(
      // sbt.test package is renamed to sbt.scriptedtest.
      exclude[MissingClassProblem]("sbt.test.*"),
    ),
  )
  .configure(addSbtIO, addSbtUtilLogging, addSbtCompilerInterface, addSbtUtilScripted, addSbtLmCore)

lazy val scriptedPluginProj = (project in scriptedPath / "plugin")
  .dependsOn(mainProj)
  .settings(
    baseSettings,
    name := "Scripted Plugin",
    mimaSettings,
    mimaBinaryIssueFilters ++= Seq(
      // scripted plugin has moved into sbt mothership.
      exclude[MissingClassProblem]("sbt.ScriptedPlugin*")
    ),
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
    mimaBinaryIssueFilters ++= Seq(
      // Removed unused private[sbt] nested class
      exclude[MissingClassProblem]("sbt.Doc$Scaladoc"),
      // Removed no longer used private[sbt] method
      exclude[DirectMissingMethodProblem]("sbt.Doc.generate"),
    ),
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
  .dependsOn(collectionProj)
  .settings(
    testedBaseSettings,
    scalacOptions -= "-Ywarn-unused",
    scalacOptions += "-Xlint:-unused",
    name := "Protocol",
    libraryDependencies ++= Seq(sjsonNewScalaJson.value, ipcSocket),
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
      exclude[DirectMissingMethodProblem]("sbt.BasicCommands.rebootOptionParser"),
      // Changed the signature of Server method. nacho cheese.
      exclude[DirectMissingMethodProblem]("sbt.internal.server.Server.*"),
      // Added method to ServerInstance. This is also internal.
      exclude[ReversedMissingMethodProblem]("sbt.internal.server.ServerInstance.*"),
      // Added method to CommandChannel. internal.
      exclude[ReversedMissingMethodProblem]("sbt.internal.CommandChannel.*"),
      // Added an overload to reboot. The overload is private[sbt].
      exclude[ReversedMissingMethodProblem]("sbt.StateOps.reboot"),
      // Replace nailgun socket stuff
      exclude[MissingClassProblem]("sbt.internal.NG*"),
      exclude[MissingClassProblem]("sbt.internal.ReferenceCountedFileDescriptor"),
      // made private[sbt] method private[this]
      exclude[DirectMissingMethodProblem]("sbt.State.handleException"),
    ),
    unmanagedSources in (Compile, headerCreate) := {
      val old = (unmanagedSources in (Compile, headerCreate)).value
      old filterNot { x => (x.getName startsWith "NG") || (x.getName == "ReferenceCountedFileDescriptor.java") }
    },
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
    baseSettings,
    name := "Core Macros",
    libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value,
    mimaSettings,
  )

// Fixes scope=Scope for Setting (core defined in collectionProj) to define the settings system used in build definitions
lazy val mainSettingsProj = (project in file("main-settings"))
  .dependsOn(completeProj, commandProj, stdTaskProj, coreMacrosProj)
  .settings(
    testedBaseSettings,
    name := "Main Settings",
    BuildInfoPlugin.buildInfoDefaultSettings,
    addBuildInfoToConfig(Test),
    buildInfoObject in Test := "TestBuildInfo",
    buildInfoKeys in Test := Seq[BuildInfoKey](
      classDirectory in Compile,
      classDirectory in Test,
      // WORKAROUND https://github.com/sbt/sbt-buildinfo/issues/117
      BuildInfoKey.map((dependencyClasspath in Compile).taskValue) { case (ident, cp) => ident -> cp.files },
    ),
    mimaSettings,
    mimaBinaryIssueFilters ++= Seq(
      exclude[DirectMissingMethodProblem]("sbt.Scope.display012StyleMasked"),
    ),
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
  .dependsOn(logicProj, actionsProj, mainSettingsProj, runProj, commandProj, collectionProj, scriptedSbtProj)
  .settings(
    testedBaseSettings,
    name := "Main",
    libraryDependencies ++= scalaXml.value ++ Seq(launcherInterface) ++ log4jDependencies ++ Seq(scalaCacheCaffeine),
    managedSourceDirectories in Compile +=
      baseDirectory.value / "src" / "main" / "contraband-scala",
    sourceManaged in (Compile, generateContrabands) := baseDirectory.value / "src" / "main" / "contraband-scala",
    mimaSettings,
    mimaBinaryIssueFilters ++= Vector(
      // New and changed methods on KeyIndex. internal.
      exclude[ReversedMissingMethodProblem]("sbt.internal.KeyIndex.*"),

      // Changed signature or removed private[sbt] methods
      exclude[DirectMissingMethodProblem]("sbt.Classpaths.unmanagedLibs0"),
      exclude[DirectMissingMethodProblem]("sbt.Defaults.allTestGroupsTask"),
      exclude[DirectMissingMethodProblem]("sbt.Plugins.topologicalSort"),
      exclude[IncompatibleMethTypeProblem]("sbt.Defaults.allTestGroupsTask"),
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
    testedBaseSettings,
    name := "sbt",
    normalizedName := "sbt",
    crossScalaVersions := Seq(baseScalaVersion),
    crossPaths := false,
    javaOptions ++= Seq("-Xdebug", "-Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"),
    mimaSettings,
    mimaBinaryIssueFilters ++= sbtIgnoredProblems,
    BuildInfoPlugin.buildInfoDefaultSettings,
    addBuildInfoToConfig(Test),
    BuildInfoPlugin.buildInfoDefaultSettings,
    buildInfoObject in Test := "TestBuildInfo",
    buildInfoKeys in Test := Seq[BuildInfoKey](
      // WORKAROUND https://github.com/sbt/sbt-buildinfo/issues/117
      BuildInfoKey.map((fullClasspath in Compile).taskValue) { case (ident, cp) => ident -> cp.files },
    ),
    connectInput in run in Test := true,
    outputStrategy in run in Test := Some(StdoutOutput),
    fork in Test := true,
    parallelExecution in Test := false,
  )
  .configure(addSbtCompilerBridge)

lazy val sbtIgnoredProblems = {
  Vector(
    exclude[MissingClassProblem]("buildinfo.BuildInfo"),
    exclude[MissingClassProblem]("buildinfo.BuildInfo$"),

    // Added more items to Import trait.
    exclude[ReversedMissingMethodProblem]("sbt.Import.sbt$Import$_setter_$WatchSource_="),
    exclude[ReversedMissingMethodProblem]("sbt.Import.WatchSource"),

    // Dropped in favour of kind-projector's polymorphic lambda literals
    exclude[DirectMissingMethodProblem]("sbt.Import.Param"),
    exclude[DirectMissingMethodProblem]("sbt.package.Param"),

    // Dropped in favour of plain scala.Function, and its compose method
    exclude[DirectMissingMethodProblem]("sbt.package.toFn1"),
  )
}

def runNpm(command: String, base: File, log: sbt.internal.util.ManagedLogger) = {
  import scala.sys.process._
  try {
    val exitCode = Process(s"npm $command", Option(base)) ! log
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
  // publishLocalBinAll.value // TODO: Restore scripted needing only binary jars.
  publishAll.value
  (sbtProj / Test / compile).value // make sure sbt.RunFromSourceMain is compiled
  Scripted.doScripted(
    (sbtLaunchJar in bundledLauncherProj).value,
    (fullClasspath in scriptedSbtProj in Test).value,
    (scalaInstance in scriptedSbtProj).value,
    scriptedSource.value,
    scriptedBufferLog.value,
    Def.setting(Scripted.scriptedParser(scriptedSource.value)).parsed,
    scriptedPrescripted.value,
    scriptedLaunchOpts.value
  )
}

def scriptedUnpublishedTask: Def.Initialize[InputTask[Unit]] = Def.inputTask {
  Scripted.doScripted(
    (sbtLaunchJar in bundledLauncherProj).value,
    (fullClasspath in scriptedSbtProj in Test).value,
    (scalaInstance in scriptedSbtProj).value,
    scriptedSource.value,
    scriptedBufferLog.value,
    Def.setting(Scripted.scriptedParser(scriptedSource.value)).parsed,
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
    scriptedLaunchOpts := List("-Xmx1500M", "-Xms512M", "-server"),
    publishAll := { val _ = (publishLocal).all(ScopeFilter(inAnyProject)).value },
    publishLocalBinAll := { val _ = (publishLocalBin).all(ScopeFilter(inAnyProject)).value },
    aggregate in bintrayRelease := false
  ) ++ inConfig(Scripted.RepoOverrideTest)(
    Seq(
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
  commands += Command.command("publishLocalAllModule") { state =>
    val extracted = Project.extract(state)
    import extracted._
    val sv      = get(scalaVersion)
    val projs   = structure.allProjectRefs
    val ioOpt   = projs find { case ProjectRef(_, id) => id == "ioRoot"; case _ => false }
    val utilOpt = projs find { case ProjectRef(_, id) => id == "utilRoot"; case _ => false }
    val lmOpt   = projs find { case ProjectRef(_, id) => id == "lmRoot"; case _ => false }
    val zincOpt = projs find { case ProjectRef(_, id) => id == "zincRoot"; case _ => false }
    (ioOpt   map { case ProjectRef(build, _) => "{" + build.toString + "}/publishLocal" }).toList :::
    (utilOpt map { case ProjectRef(build, _) => "{" + build.toString + "}/publishLocal" }).toList :::
    (lmOpt   map { case ProjectRef(build, _) => "{" + build.toString + "}/publishLocal" }).toList :::
    (zincOpt map { case ProjectRef(build, _) =>
      val zincSv = get(scalaVersion in ProjectRef(build, "zinc"))
      val csv = get(crossScalaVersions in ProjectRef(build, "compilerBridge")).toList
      (csv flatMap { bridgeSv =>
        s"++$bridgeSv" :: ("{" + build.toString + "}compilerBridge/publishLocal") :: Nil
      }) :::
      List(s"++$zincSv", "{" + build.toString + "}/publishLocal")
    }).getOrElse(Nil) :::
    List(s"++$sv", "publishLocal") :::
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

inThisBuild(Seq(
  whitesourceProduct                   := "Lightbend Reactive Platform",
  whitesourceAggregateProjectName      := "sbt-master",
  whitesourceAggregateProjectToken     := "e7a1e55518c0489a98e9c7430c8b2ccd53d9f97c12ed46148b592ebe4c8bf128",
  whitesourceIgnoredScopes            ++= Seq("plugin", "scalafmt", "sxr"),
  whitesourceFailOnError               := sys.env.contains("WHITESOURCE_PASSWORD"), // fail if pwd is present
  whitesourceForceCheckAllDependencies := true,
))
