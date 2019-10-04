import Util._
import Dependencies._
import Sxr.sxr
import com.typesafe.tools.mima.core._, ProblemFilters._
import local.Scripted
import scala.xml.{ Node => XmlNode, NodeSeq => XmlNodeSeq, _ }
import scala.xml.transform.{ RewriteRule, RuleTransformer }
import scala.util.Try

ThisBuild / version := {
  val v = "1.4.0-SNAPSHOT"
  nightlyVersion.getOrElse(v)
}
ThisBuild / scalafmtOnCompile := !(Global / insideCI).value
ThisBuild / Test / scalafmtOnCompile := !(Global / insideCI).value
ThisBuild / turbo := true

// ThisBuild settings take lower precedence,
// but can be shared across the multi projects.
def buildLevelSettings: Seq[Setting[_]] =
  inThisBuild(
    Seq(
      organization := "org.scala-sbt",
      description := "sbt is an interactive build tool",
      bintrayOrganization := Some("sbt"),
      bintrayRepository := {
        if (publishStatus.value == "releases") "maven-releases"
        else "maven-snapshots"
      },
      bintrayPackage := "sbt",
      bintrayReleaseOnPublish := false,
      licenses := List("Apache-2.0" -> url("https://github.com/sbt/sbt/blob/0.13/LICENSE")),
      javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),
      Compile / doc / javacOptions := Nil,
      developers := List(
        Developer("harrah", "Mark Harrah", "@harrah", url("https://github.com/harrah")),
        Developer("eed3si9n", "Eugene Yokota", "@eed3si9n", url("https://github.com/eed3si9n")),
        Developer("jsuereth", "Josh Suereth", "@jsuereth", url("https://github.com/jsuereth")),
        Developer("dwijnand", "Dale Wijnand", "@dwijnand", url("https://github.com/dwijnand")),
        Developer("eatkins", "Ethan Atkins", "@eatkins", url("https://github.com/eatkins")),
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
    )
  )

def commonSettings: Seq[Setting[_]] = Def.settings(
  headerLicense := Some(
    HeaderLicense.Custom(
      """|sbt
       |Copyright 2011 - 2018, Lightbend, Inc.
       |Copyright 2008 - 2010, Mark Harrah
       |Licensed under Apache License 2.0 (see LICENSE)
       |""".stripMargin
    )
  ),
  scalaVersion := baseScalaVersion,
  componentID := None,
  resolvers += Resolver.typesafeIvyRepo("releases").withName("typesafe-sbt-build-ivy-releases"),
  resolvers += Resolver.sonatypeRepo("snapshots"),
  resolvers += "bintray-sbt-maven-releases" at "https://dl.bintray.com/sbt/maven-releases/",
  resolvers += Resolver.url("bintray-scala-hedgehog", url("https://dl.bintray.com/hedgehogqa/scala-hedgehog"))(Resolver.ivyStylePatterns),
  addCompilerPlugin("org.spire-math" % "kind-projector" % "0.9.4" cross CrossVersion.binary),
  testFrameworks += TestFramework("hedgehog.sbt.Framework"),
  concurrentRestrictions in Global += Util.testExclusiveRestriction,
  testOptions in Test += Tests.Argument(TestFrameworks.ScalaCheck, "-w", "1"),
  testOptions in Test += Tests.Argument(TestFrameworks.ScalaCheck, "-verbosity", "2"),
  javacOptions in compile ++= Seq("-Xlint", "-Xlint:-serial"),
  Compile / doc / scalacOptions ++= {
    import scala.sys.process._
    val devnull = ProcessLogger(_ => ())
    val tagOrSha = ("git describe --exact-match" #|| "git rev-parse HEAD").lineStream(devnull).head
    Seq(
      "-sourcepath",
      (baseDirectory in LocalRootProject).value.getAbsolutePath,
      "-doc-source-url",
      s"https://github.com/sbt/sbt/tree/$tagOrSha€{FILE_PATH}.scala"
    )
  },
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

def sbt10Plus =
  Seq(
    "1.0.0",
    "1.0.1",
    "1.0.2",
    "1.0.3",
    "1.0.4",
    "1.1.0",
    "1.1.1",
    "1.1.2",
    "1.1.3",
    "1.1.4",
    "1.1.5",
    "1.1.6",
    "1.2.0",
    "1.2.1", /*DOA,*/ "1.2.3",
    "1.2.4", /*DOA,*/ "1.2.6",
    "1.2.7",
    "1.2.8",
  ) ++ sbt13Plus
def sbt13Plus = Seq("1.3.0")

def mimaSettings = mimaSettingsSince(sbt10Plus)
def mimaSettingsSince(versions: Seq[String]) = Def settings (
  mimaPreviousArtifacts := {
    val crossVersion = if (crossPaths.value) CrossVersion.binary else CrossVersion.disabled
    versions.map(v => organization.value % moduleName.value % v cross crossVersion).toSet
  },
  mimaBinaryIssueFilters ++= Seq(
    // Changes in the internal package
    exclude[DirectMissingMethodProblem]("sbt.internal.*"),
    exclude[FinalClassProblem]("sbt.internal.*"),
    exclude[FinalMethodProblem]("sbt.internal.*"),
    exclude[IncompatibleResultTypeProblem]("sbt.internal.*"),
    exclude[ReversedMissingMethodProblem]("sbt.internal.*")
  ),
)

val scriptedSbtReduxMimaSettings = Def settings (
  mimaPreviousArtifacts := Set()
)

lazy val sbtRoot: Project = (project in file("."))
  .enablePlugins(ScriptedPlugin) // , SiteScaladocPlugin, GhpagesPlugin)
  .configs(Sxr.SxrConf)
  .aggregate(nonRoots: _*)
  .settings(
    buildLevelSettings,
    minimalSettings,
    onLoadMessage := {
      val version = sys.props("java.specification.version")
      """           __    __ 
        |     _____/ /_  / /_
        |    / ___/ __ \/ __/
        |   (__  ) /_/ / /_  
        |  /____/_.___/\__/ 
        |Welcome to the build for sbt.
        |""".stripMargin +
        (if (version != "1.8")
           s"""!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
               |  Java version is $version. We recommend java 8.
               |!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!""".stripMargin
         else "")
    },
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
    commands in Global += Command
      .single("sbtOn")((state, dir) => s"sbtProj/test:runMain sbt.RunFromSourceMain $dir" :: state),
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
    // Parser is used publicly, so we can't break bincompat.
    mimaBinaryIssueFilters := Seq(
      exclude[DirectMissingMethodProblem]("sbt.internal.util.complete.History.this"),
    ),
  )
  .configure(addSbtIO, addSbtUtilControl, addSbtUtilLogging)

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
    libraryDependencies ++= scalaXml.value ++ Seq(
      testInterface,
      launcherInterface,
      sjsonNewScalaJson.value
    ),
    Compile / scalacOptions += "-Ywarn-unused:-locals,-explicits,-privates",
    managedSourceDirectories in Compile +=
      baseDirectory.value / "src" / "main" / "contraband-scala",
    sourceManaged in (Compile, generateContrabands) := baseDirectory.value / "src" / "main" / "contraband-scala",
    contrabandFormatsForType in generateContrabands in Compile := ContrabandConfig.getFormats,
    mimaSettings,
    mimaBinaryIssueFilters ++= Seq(
      // private[sbt]
      exclude[IncompatibleMethTypeProblem]("sbt.TestStatus.write"),
      exclude[IncompatibleResultTypeProblem]("sbt.TestStatus.read"),
      // copy method was never meant to be public
      exclude[DirectMissingMethodProblem]("sbt.protocol.testing.EndTestGroupErrorEvent.copy"),
      exclude[DirectMissingMethodProblem](
        "sbt.protocol.testing.EndTestGroupErrorEvent.copy$default$*"
      ),
      exclude[DirectMissingMethodProblem]("sbt.protocol.testing.EndTestGroupEvent.copy"),
      exclude[DirectMissingMethodProblem]("sbt.protocol.testing.EndTestGroupEvent.copy$default$*"),
      exclude[DirectMissingMethodProblem]("sbt.protocol.testing.StartTestGroupEvent.copy"),
      exclude[DirectMissingMethodProblem](
        "sbt.protocol.testing.StartTestGroupEvent.copy$default$*"
      ),
      exclude[DirectMissingMethodProblem]("sbt.protocol.testing.TestCompleteEvent.copy"),
      exclude[DirectMissingMethodProblem]("sbt.protocol.testing.TestCompleteEvent.copy$default$*"),
      exclude[DirectMissingMethodProblem]("sbt.protocol.testing.TestInitEvent.copy"),
      exclude[DirectMissingMethodProblem]("sbt.protocol.testing.TestItemDetail.copy"),
      exclude[DirectMissingMethodProblem]("sbt.protocol.testing.TestItemDetail.copy$default$*"),
      exclude[DirectMissingMethodProblem]("sbt.protocol.testing.TestItemEvent.copy"),
      exclude[DirectMissingMethodProblem]("sbt.protocol.testing.TestItemEvent.copy$default$*"),
      exclude[DirectMissingMethodProblem]("sbt.protocol.testing.TestStringEvent.copy"),
      exclude[DirectMissingMethodProblem]("sbt.protocol.testing.TestStringEvent.copy$default$1"),
      //no reason to use
      exclude[DirectMissingMethodProblem]("sbt.JUnitXmlTestsListener.testSuite"),
    )
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
    mimaBinaryIssueFilters ++= Seq(
      // ok because sbt.ExecuteProgress has been under private[sbt]
      exclude[IncompatibleResultTypeProblem]("sbt.ExecuteProgress.initial"),
      exclude[DirectMissingMethodProblem]("sbt.ExecuteProgress.*"),
      exclude[ReversedMissingMethodProblem]("sbt.ExecuteProgress.*"),
    )
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
    mimaBinaryIssueFilters ++= Seq(
      // unused private[sbt]
      exclude[DirectMissingMethodProblem]("sbt.Task.mapTask"),
    ),
  )
  .configure(addSbtIO, addSbtUtilLogging, addSbtUtilCache)

// Embedded Scala code runner
lazy val runProj = (project in file("run"))
  .enablePlugins(ContrabandPlugin)
  .dependsOn(collectionProj)
  .settings(
    testedBaseSettings,
    name := "Run",
    Compile / scalacOptions += "-Ywarn-unused:-locals,-explicits,-privates",
    managedSourceDirectories in Compile +=
      baseDirectory.value / "src" / "main" / "contraband-scala",
    sourceManaged in (Compile, generateContrabands) := baseDirectory.value / "src" / "main" / "contraband-scala",
    mimaSettings,
    mimaBinaryIssueFilters ++= Seq(
      // copy method was never meant to be public
      exclude[DirectMissingMethodProblem]("sbt.ForkOptions.copy"),
      exclude[DirectMissingMethodProblem]("sbt.ForkOptions.copy$default$*"),
      exclude[DirectMissingMethodProblem]("sbt.OutputStrategy#BufferedOutput.copy"),
      exclude[DirectMissingMethodProblem]("sbt.OutputStrategy#BufferedOutput.copy$default$*"),
      exclude[DirectMissingMethodProblem]("sbt.OutputStrategy#CustomOutput.copy"),
      exclude[DirectMissingMethodProblem]("sbt.OutputStrategy#CustomOutput.copy$default$*"),
      exclude[DirectMissingMethodProblem]("sbt.OutputStrategy#LoggedOutput.copy"),
      exclude[DirectMissingMethodProblem]("sbt.OutputStrategy#LoggedOutput.copy$default$*"),
    )
  )
  .configure(addSbtIO, addSbtUtilLogging, addSbtUtilControl, addSbtCompilerClasspath)

val sbtProjDepsCompileScopeFilter =
  ScopeFilter(
    inDependencies(LocalProject("sbtProj"), includeRoot = false),
    inConfigurations(Compile)
  )

lazy val scriptedSbtReduxProj = (project in file("scripted-sbt-redux"))
  .dependsOn(commandProj)
  .settings(
    baseSettings,
    name := "Scripted sbt Redux",
    libraryDependencies ++= Seq(launcherInterface % "provided"),
    resourceGenerators in Compile += Def task {
      val mainClassDir = (classDirectory in Compile in LocalProject("sbtProj")).value
      val testClassDir = (classDirectory in Test in LocalProject("sbtProj")).value
      val classDirs = (classDirectory all sbtProjDepsCompileScopeFilter).value
      val extDepsCp = (externalDependencyClasspath in Compile in LocalProject("sbtProj")).value
      val cpStrings = (mainClassDir +: testClassDir +: classDirs) ++ extDepsCp.files map (_.toString)
      val file = (resourceManaged in Compile).value / "RunFromSource.classpath"
      if (!file.exists || Try(IO.readLines(file)).getOrElse(Nil).toSet != cpStrings.toSet) {
        IO.writeLines(file, cpStrings)
      }
      List(file)
    },
    mimaSettings,
    scriptedSbtReduxMimaSettings,
  )
  .configure(addSbtIO, addSbtUtilLogging, addSbtCompilerInterface, addSbtUtilScripted, addSbtLmCore)

lazy val scriptedSbtOldProj = (project in file("scripted-sbt-old"))
  .dependsOn(scriptedSbtReduxProj)
  .settings(
    baseSettings,
    name := "Scripted sbt",
    mimaSettings,
    mimaBinaryIssueFilters ++= Seq(
      // sbt.test package is renamed to sbt.scriptedtest.
      exclude[MissingClassProblem]("sbt.test.*"),
      exclude[DirectMissingMethodProblem]("sbt.test.*"),
      exclude[IncompatibleMethTypeProblem]("sbt.test.*"),
    ),
  )

lazy val scriptedPluginProj = (project in file("scripted-plugin"))
  .settings(
    baseSettings,
    name := "Scripted Plugin",
    mimaSettings,
    mimaBinaryIssueFilters ++= Seq(
      // scripted plugin has moved into sbt mothership.
      exclude[MissingClassProblem]("sbt.ScriptedPlugin*")
    ),
  )

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
      exclude[DirectMissingMethodProblem]("sbt.compiler.Eval.filesModifiedBytes"),
      exclude[DirectMissingMethodProblem]("sbt.compiler.Eval.fileModifiedBytes"),
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
    addSbtZinc
  )

lazy val protocolProj = (project in file("protocol"))
  .enablePlugins(ContrabandPlugin, JsonCodecPlugin)
  .dependsOn(collectionProj)
  .settings(
    testedBaseSettings,
    name := "Protocol",
    libraryDependencies ++= Seq(sjsonNewScalaJson.value, ipcSocket),
    Compile / scalacOptions += "-Ywarn-unused:-locals,-explicits,-privates",
    managedSourceDirectories in Compile +=
      baseDirectory.value / "src" / "main" / "contraband-scala",
    sourceManaged in (Compile, generateContrabands) := baseDirectory.value / "src" / "main" / "contraband-scala",
    contrabandFormatsForType in generateContrabands in Compile := ContrabandConfig.getFormats,
    mimaSettings,
    mimaBinaryIssueFilters ++= Seq(
      // copy method was never meant to be public
      exclude[DirectMissingMethodProblem]("sbt.protocol.ChannelAcceptedEvent.copy"),
      exclude[DirectMissingMethodProblem]("sbt.protocol.ChannelAcceptedEvent.copy$default$1"),
      exclude[DirectMissingMethodProblem]("sbt.protocol.ExecCommand.copy"),
      exclude[DirectMissingMethodProblem]("sbt.protocol.ExecCommand.copy$default$1"),
      exclude[DirectMissingMethodProblem]("sbt.protocol.ExecCommand.copy$default$2"),
      exclude[DirectMissingMethodProblem]("sbt.protocol.ExecStatusEvent.copy"),
      exclude[DirectMissingMethodProblem]("sbt.protocol.ExecStatusEvent.copy$default$*"),
      exclude[DirectMissingMethodProblem]("sbt.protocol.ExecutionEvent.copy"),
      exclude[DirectMissingMethodProblem]("sbt.protocol.ExecutionEvent.copy$default$*"),
      exclude[DirectMissingMethodProblem]("sbt.protocol.InitCommand.copy"),
      exclude[DirectMissingMethodProblem]("sbt.protocol.InitCommand.copy$default$*"),
      exclude[DirectMissingMethodProblem]("sbt.protocol.LogEvent.copy"),
      exclude[DirectMissingMethodProblem]("sbt.protocol.LogEvent.copy$default$*"),
      exclude[DirectMissingMethodProblem]("sbt.protocol.SettingQuery.copy"),
      exclude[DirectMissingMethodProblem]("sbt.protocol.SettingQuery.copy$default$1"),
      exclude[DirectMissingMethodProblem]("sbt.protocol.SettingQueryFailure.copy"),
      exclude[DirectMissingMethodProblem]("sbt.protocol.SettingQueryFailure.copy$default$*"),
      exclude[DirectMissingMethodProblem]("sbt.protocol.SettingQuerySuccess.copy"),
      exclude[DirectMissingMethodProblem]("sbt.protocol.SettingQuerySuccess.copy$default$*"),
      // ignore missing methods in sbt.internal
      exclude[DirectMissingMethodProblem]("sbt.internal.*"),
    )
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
    Compile / scalacOptions += "-Ywarn-unused:-locals,-explicits,-privates",
    // Removing -Xfatal-warnings is necessary because BasicKeys contains a Key for a deprecated class.
    Compile / scalacOptions -= "-Xfatal-warnings",
    managedSourceDirectories in Compile +=
      baseDirectory.value / "src" / "main" / "contraband-scala",
    sourceManaged in (Compile, generateContrabands) := baseDirectory.value / "src" / "main" / "contraband-scala",
    contrabandFormatsForType in generateContrabands in Compile := ContrabandConfig.getFormats,
    mimaSettings,
    mimaBinaryIssueFilters ++= Vector(
      // dropped private[sbt] method
      exclude[DirectMissingMethodProblem]("sbt.BasicCommands.compatCommands"),
      // dropped mainly internal command strings holder
      exclude[MissingClassProblem]("sbt.BasicCommandStrings$Compat$"),
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
      // copy method was never meant to be public
      exclude[DirectMissingMethodProblem]("sbt.CommandSource.copy"),
      exclude[DirectMissingMethodProblem]("sbt.CommandSource.copy$default$*"),
      exclude[DirectMissingMethodProblem]("sbt.Exec.copy"),
      exclude[DirectMissingMethodProblem]("sbt.Exec.copy$default$*"),
      // internal
      exclude[ReversedMissingMethodProblem]("sbt.internal.client.ServerConnection.*"),
    ),
    unmanagedSources in (Compile, headerCreate) := {
      val old = (unmanagedSources in (Compile, headerCreate)).value
      old filterNot { x =>
        (x.getName startsWith "NG") || (x.getName == "ReferenceCountedFileDescriptor.java")
      }
    },
  )
  .configure(
    addSbtIO,
    addSbtUtilLogging,
    addSbtCompilerInterface,
    addSbtCompilerClasspath,
    addSbtLmCore,
    addSbtZinc
  )

// The core macro project defines the main logic of the DSL, abstracted
// away from several sbt implementors (tasks, settings, et cetera).
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
      BuildInfoKey.map((dependencyClasspath in Compile).taskValue) {
        case (ident, cp) => ident -> cp.files
      },
    ),
    mimaSettings,
    mimaBinaryIssueFilters ++= Seq(
      exclude[DirectMissingMethodProblem]("sbt.Scope.display012StyleMasked"),
      // added a method to a sealed trait
      exclude[InheritedNewAbstractMethodProblem]("sbt.Scoped.canEqual"),
      exclude[InheritedNewAbstractMethodProblem]("sbt.ScopedTaskable.canEqual"),
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

lazy val zincLmIntegrationProj = (project in file("zinc-lm-integration"))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    name := "Zinc LM Integration",
    testedBaseSettings,
    buildInfo in Compile := Nil, // Only generate build info for tests
    BuildInfoPlugin.buildInfoScopedSettings(Test),
    buildInfoPackage in Test := "sbt.internal.inc",
    buildInfoObject in Test := "ZincLmIntegrationBuildInfo",
    buildInfoKeys in Test := List[BuildInfoKey]("zincVersion" -> zincVersion),
    mimaSettingsSince(sbt13Plus),
    libraryDependencies += launcherInterface,
  )
  .configure(addSbtZincCompileCore, addSbtLmCore, addSbtLmIvyTest)

// The main integration project for sbt.  It brings all of the projects together, configures them, and provides for overriding conventions.
lazy val mainProj = (project in file("main"))
  .enablePlugins(ContrabandPlugin)
  .dependsOn(
    logicProj,
    actionsProj,
    mainSettingsProj,
    runProj,
    commandProj,
    collectionProj,
    scriptedSbtReduxProj,
    scriptedPluginProj,
    zincLmIntegrationProj
  )
  .settings(
    testedBaseSettings,
    name := "Main",
    checkPluginCross := {
      val sv = scalaVersion.value
      val xs =
        IO.readLines(baseDirectory.value / "src" / "main" / "scala" / "sbt" / "PluginCross.scala")
      if (xs exists { s =>
            s.contains(s""""$sv"""")
          }) ()
      else sys.error("PluginCross.scala does not match up with the scalaVersion " + sv)
    },
    libraryDependencies ++= {
      scalaXml.value ++
        Seq(launcherInterface) ++
        log4jDependencies ++
        Seq(scalaCacheCaffeine, lmCoursierShaded)
    },
    Compile / scalacOptions -= "-Xfatal-warnings",
    managedSourceDirectories in Compile +=
      baseDirectory.value / "src" / "main" / "contraband-scala",
    sourceManaged in (Compile, generateContrabands) := baseDirectory.value / "src" / "main" / "contraband-scala",
    testOptions in Test += Tests
      .Argument(TestFrameworks.ScalaCheck, "-minSuccessfulTests", "1000"),
    mimaSettings,
    mimaBinaryIssueFilters ++= Vector(
      // New and changed methods on KeyIndex. internal.
      exclude[ReversedMissingMethodProblem]("sbt.internal.KeyIndex.*"),
      // internal
      exclude[IncompatibleMethTypeProblem]("sbt.internal.server.LanguageServerReporter.*"),

      // Changed signature or removed private[sbt] methods
      exclude[DirectMissingMethodProblem]("sbt.Classpaths.unmanagedLibs0"),
      exclude[DirectMissingMethodProblem]("sbt.Defaults.allTestGroupsTask"),
      exclude[DirectMissingMethodProblem]("sbt.Plugins.topologicalSort"),
      exclude[IncompatibleMethTypeProblem]("sbt.Defaults.allTestGroupsTask"),
      exclude[DirectMissingMethodProblem]("sbt.StandardMain.shutdownHook"),
      exclude[MissingClassProblem]("sbt.internal.ResourceLoaderImpl"),
      // Removed private internal classes
      exclude[MissingClassProblem]("sbt.internal.ReverseLookupClassLoaderHolder$BottomClassLoader"),
      exclude[MissingClassProblem]("sbt.internal.ReverseLookupClassLoaderHolder$ReverseLookupClassLoader$ResourceLoader"),
      exclude[MissingClassProblem]("sbt.internal.ReverseLookupClassLoaderHolder$ClassLoadingLock"),
      exclude[MissingClassProblem]("sbt.internal.ReverseLookupClassLoaderHolder$ReverseLookupClassLoader"),
      exclude[MissingClassProblem]("sbt.internal.LayeredClassLoaderImpl"),
    )
  )
  .configure(
    addSbtIO,
    addSbtUtilLogging,
    addSbtLmCore,
    addSbtLmImpl,
    addSbtCompilerInterface,
    addSbtZincCompile
  )

// Strictly for bringing implicits and aliases from subsystems into the top-level sbt namespace through a single package object
//  technically, we need a dependency on all of mainProj's dependencies, but we don't do that since this is strictly an integration project
//  with the sole purpose of providing certain identifiers without qualification (with a package object)
lazy val sbtProj = (project in file("sbt"))
  .dependsOn(mainProj, scriptedSbtReduxProj % "test->test")
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
      version,
      // WORKAROUND https://github.com/sbt/sbt-buildinfo/issues/117
      BuildInfoKey.map((fullClasspath in Compile).taskValue) {
        case (ident, cp) => ident -> cp.files
      },
      BuildInfoKey.map((dependencyClasspath in Compile).taskValue) {
        case (ident, cp) => ident -> cp.files
      },
      classDirectory in Compile,
      classDirectory in Test,
    ),
    Test / run / connectInput := true,
    Test / run / outputStrategy := Some(StdoutOutput),
    Test / run / fork := true,
  )
  .configure(addSbtIO, addSbtCompilerBridge)

/*
lazy val sbtBig = (project in file(".big"))
  .dependsOn(sbtProj)
  .settings(
    name := "sbt-big",
    normalizedName := "sbt-big",
    crossPaths := false,
    assemblyShadeRules.in(assembly) := {
      val packagesToBeShaded = Seq(
        "fastparse",
        "jawn",
        "scalapb",
      )
      packagesToBeShaded.map( prefix => {
        ShadeRule.rename(s"$prefix.**" -> s"sbt.internal.$prefix.@1").inAll
      })
    },
    assemblyMergeStrategy in assembly := {
      case "LICENSE" | "NOTICE" => MergeStrategy.first
      case x => (assemblyMergeStrategy in assembly).value(x)
    },
    artifact.in(Compile, packageBin) := artifact.in(Compile, assembly).value,
    assemblyOption.in(assembly) ~= { _.copy(includeScala = false) },
    addArtifact(artifact.in(Compile, packageBin), assembly),
    pomPostProcess := { node =>
      new RuleTransformer(new RewriteRule {
        override def transform(node: XmlNode): XmlNodeSeq = node match {
          case e: Elem if node.label == "dependency" =>
            Comment(
              "the dependency that was here has been absorbed via sbt-assembly"
            )
          case _ => node
        }
      }).transform(node).head
    },
  )
 */

lazy val sbtIgnoredProblems = {
  Vector(
    exclude[MissingClassProblem]("buildinfo.BuildInfo"),
    exclude[MissingClassProblem]("buildinfo.BuildInfo$"),
    // Added more items to Import trait.
    exclude[ReversedMissingMethodProblem]("sbt.Import.sbt$Import$_setter_$WatchSource_="),
    exclude[ReversedMissingMethodProblem]("sbt.Import.WatchSource"),
    exclude[ReversedMissingMethodProblem]("sbt.Import.AnyPath"),
    exclude[ReversedMissingMethodProblem]("sbt.Import.sbt$Import$_setter_$**_="),
    exclude[ReversedMissingMethodProblem]("sbt.Import.sbt$Import$_setter_$*_="),
    exclude[ReversedMissingMethodProblem]("sbt.Import.sbt$Import$_setter_$ChangedFiles_="),
    exclude[ReversedMissingMethodProblem]("sbt.Import.sbt$Import$_setter_$AnyPath_="),
    exclude[ReversedMissingMethodProblem]("sbt.Import.sbt$Import$_setter_$Glob_="),
    exclude[ReversedMissingMethodProblem]("sbt.Import.sbt$Import$_setter_$RecursiveGlob_="),
    exclude[ReversedMissingMethodProblem]("sbt.Import.sbt$Import$_setter_$RelativeGlob_="),
    exclude[ReversedMissingMethodProblem]("sbt.Import.*"),
    exclude[ReversedMissingMethodProblem]("sbt.Import.**"),
    exclude[ReversedMissingMethodProblem]("sbt.Import.ChangedFiles"),
    exclude[ReversedMissingMethodProblem]("sbt.Import.RecursiveGlob"),
    exclude[ReversedMissingMethodProblem]("sbt.Import.Glob"),
    exclude[ReversedMissingMethodProblem]("sbt.Import.RelativeGlob"),
    // Dropped in favour of kind-projector's polymorphic lambda literals
    exclude[DirectMissingMethodProblem]("sbt.Import.Param"),
    exclude[DirectMissingMethodProblem]("sbt.package.Param"),
    exclude[ReversedMissingMethodProblem]("sbt.Import.SemanticSelector"),
    exclude[ReversedMissingMethodProblem]("sbt.Import.sbt$Import$_setter_$SemanticSelector_="),
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
        base / "node_modules",
        base / "client" / "node_modules",
        base / "client" / "server",
        base / "client" / "out",
        base / "server" / "node_modules"
      ) filter { _.exists }
    }
  )

def scriptedTask: Def.Initialize[InputTask[Unit]] = Def.inputTask {
  // publishLocalBinAll.value // TODO: Restore scripted needing only binary jars.
  publishAll.value
  (sbtProj / Test / compile).value // make sure sbt.RunFromSourceMain is compiled
  Scripted.doScripted(
    (sbtLaunchJar in bundledLauncherProj).value,
    (fullClasspath in scriptedSbtReduxProj in Test).value,
    (scalaInstance in scriptedSbtReduxProj).value,
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
    (fullClasspath in scriptedSbtReduxProj in Test).value,
    (scalaInstance in scriptedSbtReduxProj).value,
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
    scriptedSbtReduxProj,
    scriptedSbtOldProj,
    scriptedPluginProj,
    protocolProj,
    actionsProj,
    commandProj,
    mainSettingsProj,
    zincLmIntegrationProj,
    mainProj,
    sbtProj,
    bundledLauncherProj,
    coreMacrosProj
  )

lazy val nonRoots = allProjects.map(p => LocalProject(p.id))

ThisBuild / scriptedBufferLog := true
ThisBuild / scriptedPrescripted := { _ =>
}

def otherRootSettings =
  Seq(
    scripted := scriptedTask.evaluated,
    scriptedUnpublished := scriptedUnpublishedTask.evaluated,
    scriptedSource := (sourceDirectory in sbtProj).value / "sbt-test",
    watchTriggers in scripted += scriptedSource.value.toGlob / **,
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
    )
  )

lazy val docProjects: ScopeFilter = ScopeFilter(
  inAnyProject -- inProjects(
    sbtRoot,
    sbtProj,
    scriptedSbtReduxProj,
    scriptedSbtOldProj,
    scriptedPluginProj
  ),
  inConfigurations(Compile)
)
lazy val safeUnitTests = taskKey[Unit]("Known working tests (for both 2.10 and 2.11)")
lazy val safeProjects: ScopeFilter = ScopeFilter(
  inAnyProject -- inProjects(sbtRoot, sbtProj),
  inConfigurations(Test)
)
lazy val otherUnitTests = taskKey[Unit]("Unit test other projects")
lazy val otherProjects: ScopeFilter = ScopeFilter(
  inProjects(
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
  commands += Command.command("whitesourceOnPush") { state =>
    sys.env.get("TRAVIS_EVENT_TYPE") match {
      case Some("push") =>
        "whitesourceCheckPolicies" ::
          "whitesourceUpdate" ::
          state
      case _ => state
    }
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
    val sv = get(scalaVersion)
    val projs = structure.allProjectRefs
    val ioOpt = projs find { case ProjectRef(_, id)   => id == "ioRoot"; case _ => false }
    val utilOpt = projs find { case ProjectRef(_, id) => id == "utilRoot"; case _ => false }
    val lmOpt = projs find { case ProjectRef(_, id)   => id == "lmRoot"; case _ => false }
    val zincOpt = projs find { case ProjectRef(_, id) => id == "zincRoot"; case _ => false }
    (ioOpt map { case ProjectRef(build, _)            => "{" + build.toString + "}/publishLocal" }).toList :::
      (utilOpt map { case ProjectRef(build, _)        => "{" + build.toString + "}/publishLocal" }).toList :::
      (lmOpt map { case ProjectRef(build, _)          => "{" + build.toString + "}/publishLocal" }).toList :::
      (zincOpt map {
        case ProjectRef(build, _) =>
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

ThisBuild / whitesourceProduct := "Lightbend Reactive Platform"
ThisBuild / whitesourceAggregateProjectName := {
  // note this can get detached on tag build etc
  val b = (ThisBuild / git.gitCurrentBranch).value
  val Stable = """1\.([0-9]+)\.x""".r
  b match {
    case Stable(y) => "sbt-1." + y.toString + "-stable"
    case _         => "sbt-master"
  }
}
ThisBuild / whitesourceAggregateProjectToken := {
  (ThisBuild / whitesourceAggregateProjectName).value match {
    case "sbt-master"     => "e7a1e55518c0489a98e9c7430c8b2ccd53d9f97c12ed46148b592ebe4c8bf128"
    case "sbt-1.2-stable" => "54f2313767aa47198971e65595670ee16e1ad0000d20458588e72d3ac2c34763"
    case _                => "" // it's ok to fail here
  }
}
ThisBuild / whitesourceIgnoredScopes ++= Seq("plugin", "scalafmt", "sxr")
ThisBuild / whitesourceFailOnError := sys.env.contains("WHITESOURCE_PASSWORD") // fail if pwd is present
ThisBuild / whitesourceForceCheckAllDependencies := true
