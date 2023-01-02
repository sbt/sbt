import Dependencies._
import Util._
import com.typesafe.tools.mima.core.ProblemFilters._
import com.typesafe.tools.mima.core._
import local.Scripted
import java.nio.file.{ Files, Path => JPath }
import java.util.Locale

import scala.util.Try

// ThisBuild settings take lower precedence,
// but can be shared across the multi projects.
ThisBuild / version := {
  val v = "1.8.1-SNAPSHOT"
  nightlyVersion.getOrElse(v)
}
ThisBuild / version2_13 := "2.0.0-SNAPSHOT"
ThisBuild / versionScheme := Some("early-semver")
ThisBuild / scalafmtOnCompile := !(Global / insideCI).value
ThisBuild / Test / scalafmtOnCompile := !(Global / insideCI).value
ThisBuild / turbo := true
ThisBuild / usePipelining := false // !(Global / insideCI).value
ThisBuild / organization := "org.scala-sbt"
ThisBuild / description := "sbt is an interactive build tool"
ThisBuild / licenses := List("Apache-2.0" -> url("https://github.com/sbt/sbt/blob/develop/LICENSE"))
ThisBuild / javacOptions ++= Seq("-source", "1.8", "-target", "1.8")
ThisBuild / Compile / doc / javacOptions := Nil
ThisBuild / developers := List(
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
)
ThisBuild / homepage := Some(url("https://github.com/sbt/sbt"))
ThisBuild / scmInfo := Some(
  ScmInfo(url("https://github.com/sbt/sbt"), "git@github.com:sbt/sbt.git")
)
ThisBuild / resolvers += Resolver.mavenLocal
ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always

Global / semanticdbEnabled := !(Global / insideCI).value
// Change main/src/main/scala/sbt/plugins/SemanticdbPlugin.scala too, if you change this.
Global / semanticdbVersion := "4.5.13"
val excludeLint = SettingKey[Set[Def.KeyedInitialize[_]]]("excludeLintKeys")
Global / excludeLint := (Global / excludeLint).?.value.getOrElse(Set.empty)
Global / excludeLint += componentID
Global / excludeLint += scriptedBufferLog
Global / excludeLint += checkPluginCross

def commonBaseSettings: Seq[Setting[_]] = Def.settings(
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
  testFrameworks += TestFramework("hedgehog.sbt.Framework"),
  testFrameworks += TestFramework("verify.runner.Framework"),
  Global / concurrentRestrictions += Util.testExclusiveRestriction,
  Test / testOptions += Tests.Argument(TestFrameworks.ScalaCheck, "-w", "1"),
  Test / testOptions += Tests.Argument(TestFrameworks.ScalaCheck, "-verbosity", "2"),
  compile / javacOptions ++= Seq("-Xlint", "-Xlint:-serial"),
  /*
  Compile / doc / scalacOptions ++= {
    import scala.sys.process._
    val devnull = ProcessLogger(_ => ())
    val tagOrSha = ("git describe --exact-match" #|| "git rev-parse HEAD").lineStream(devnull).head
    Seq(
      "-sourcepath",
      (LocalRootProject / baseDirectory).value.getAbsolutePath,
      "-doc-source-url",
      s"https://github.com/sbt/sbt/tree/$tagOrSha€{FILE_PATH}.scala"
    )
  },
   */
  Compile / javafmtOnCompile := Def
    .taskDyn(if ((scalafmtOnCompile).value) Compile / javafmt else Def.task(()))
    .value,
  Test / javafmtOnCompile := Def
    .taskDyn(if ((Test / scalafmtOnCompile).value) Test / javafmt else Def.task(()))
    .value,
  Compile / unmanagedSources / inputFileStamps :=
    (Compile / unmanagedSources / inputFileStamps).dependsOn(Compile / javafmtOnCompile).value,
  Test / unmanagedSources / inputFileStamps :=
    (Test / unmanagedSources / inputFileStamps).dependsOn(Test / javafmtOnCompile).value,
  crossScalaVersions := List(scala212, scala213),
  Test / publishArtifact := false,
  run / fork := true,
)
def commonSettings: Seq[Setting[_]] =
  commonBaseSettings :+ {
    libraryDependencies ++= {
      if (scalaBinaryVersion.value == "3") {
        Nil
      } else {
        Seq(compilerPlugin(kindProjector))
      }
    }
  }
def utilCommonSettings: Seq[Setting[_]] =
  baseSettings :+ (crossScalaVersions := (scala212 :: scala213 :: Nil))

def minimalSettings: Seq[Setting[_]] =
  commonSettings ++ customCommands ++
    publishPomSettings

def baseSettings: Seq[Setting[_]] =
  minimalSettings ++ Seq(projectComponent) ++ baseScalacOptions ++ Licensed.settings

def testedBaseSettings: Seq[Setting[_]] =
  baseSettings ++ testDependencies

val sbt13Plus =
  Seq(
    "1.3.0",
    "1.4.0",
    "1.5.0",
    "1.6.0",
    "1.7.0",
  )
val sbt10Plus =
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
    "1.2.1",
    /*DOA,*/ "1.2.3",
    "1.2.4",
    /*DOA,*/ "1.2.6",
    "1.2.7",
    "1.2.8",
  ) ++ sbt13Plus
val noUtilVersion =
  Set("1.0.4", "1.1.4", "1.1.5", "1.1.6", "1.2.3", "1.2.4", "1.2.6", "1.2.7", "1.2.8")

val mimaSettings = mimaSettingsSince(sbt10Plus)
val utilMimaSettings = mimaSettingsSince(sbt10Plus.filterNot(noUtilVersion))
def mimaSettingsSince(versions: Seq[String]): Seq[Def.Setting[_]] = Def settings (
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
    exclude[ReversedMissingMethodProblem]("sbt.internal.*"),
    exclude[DirectMissingMethodProblem]("sbt.PluginData.apply"),
    exclude[DirectMissingMethodProblem]("sbt.PluginData.copy"),
    exclude[DirectMissingMethodProblem]("sbt.PluginData.this"),
  ),
)

val scriptedSbtReduxMimaSettings = Def.settings(mimaPreviousArtifacts := Set())

lazy val sbtRoot: Project = (project in file("."))
// .enablePlugins(ScriptedPlugin)
  .aggregate(nonRoots: _*)
  .settings(
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
    scalacOptions += "-Ymacro-expand:none", // for both sxr and doc
    Util.publishPomSettings,
    otherRootSettings,
    Transform.conscriptSettings(bundledLauncherProj),
    publish := {},
    publishLocal := {},
    publish / skip := true,
    Global / commands += Command
      .single("sbtOn")((state, dir) => s"sbtProj/test:runMain sbt.RunFromSourceMain $dir" :: state),
    mimaSettings,
    mimaPreviousArtifacts := Set.empty,
    buildThinClient := (sbtClientProj / buildThinClient).evaluated,
    nativeImage := (sbtClientProj / nativeImage).value,
    installNativeThinClient := {
      // nativeInstallDirectory can be set globally or in a gitignored local file
      val dir = nativeInstallDirectory.?.value
      val target = Def.spaceDelimited("").parsed.headOption match {
        case Some(p) => file(p).toPath
        case _ =>
          dir match {
            case Some(d) => d / "sbtn"
            case _ =>
              val msg = "Expected input parameter <path>: installNativeExecutable /usr/local/bin"
              throw new IllegalStateException(msg)
          }
      }
      val base = baseDirectory.value.toPath
      val exec = (sbtClientProj / nativeImage).value.toPath
      streams.value.log.info(s"installing thin client ${base.relativize(exec)} to ${target}")
      Files.copy(exec, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }
  )

// This is used to configure an sbt-launcher for this version of sbt.
lazy val bundledLauncherProj =
  (project in file("launch"))
    .enablePlugins(SbtLauncherPlugin)
    .settings(
      minimalSettings,
      inConfig(Compile)(Transform.configSettings),
    )
    .settings(
      name := "sbt-launch",
      moduleName := "sbt-launch",
      description := "sbt application launcher",
      autoScalaLibrary := false,
      crossPaths := false,
      Compile / doc / javacOptions := Nil,
      Compile / packageBin := sbtLaunchJar.value,
      mimaSettings,
      mimaPreviousArtifacts := Set()
    )

/* ** subproject declarations ** */

val collectionProj = (project in file("internal") / "util-collection")
  .settings(
    testedBaseSettings,
    utilCommonSettings,
    Util.keywordsSettings,
    name := "Collections",
    libraryDependencies ++= Seq(sjsonNewScalaJson.value),
    libraryDependencies ++= (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, major)) if major <= 12 => Seq()
      case _                               => Seq("org.scala-lang.modules" %% "scala-parallel-collections" % "0.2.0")
    }),
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
      exclude[IncompatibleSignatureProblem]("sbt.internal.util.Init*.*"),
      exclude[IncompatibleSignatureProblem]("sbt.internal.util.Settings0.*"),
      exclude[IncompatibleSignatureProblem]("sbt.internal.util.EvaluateSettings#INode.*"),
      exclude[IncompatibleSignatureProblem]("sbt.internal.util.TypeFunctions.*"),
      exclude[IncompatibleSignatureProblem]("sbt.internal.util.EvaluateSettings.*"),
      exclude[IncompatibleSignatureProblem]("sbt.internal.util.Settings.*"),
      exclude[IncompatibleSignatureProblem]("sbt.internal.util.EvaluateSettings#MixedNode.*"),
      exclude[IncompatibleSignatureProblem]("sbt.internal.util.EvaluateSettings#BindNode.this"),
      exclude[IncompatibleSignatureProblem](
        "sbt.internal.util.EvaluateSettings#BindNode.dependsOn"
      ),
      exclude[IncompatibleSignatureProblem]("sbt.internal.util.Types.some")
    ),
  )
  .dependsOn(utilPosition)

// Command line-related utilities.
val completeProj = (project in file("internal") / "util-complete")
  .dependsOn(collectionProj, utilControl, utilLogging)
  .settings(
    testedBaseSettings,
    name := "Completion",
    libraryDependencies += jline,
    libraryDependencies += jline3Reader,
    libraryDependencies += jline3Builtins,
    mimaSettings,
    // Parser is used publicly, so we can't break bincompat.
    mimaBinaryIssueFilters := Seq(
      exclude[DirectMissingMethodProblem]("sbt.internal.util.complete.SoftInvalid.apply"),
      exclude[DirectMissingMethodProblem]("sbt.internal.util.complete.Invalid.apply"),
      exclude[DirectMissingMethodProblem]("sbt.internal.util.complete.Finite.apply"),
      exclude[DirectMissingMethodProblem]("sbt.internal.util.complete.Infinite.decrement"),
      exclude[DirectMissingMethodProblem]("sbt.internal.util.complete.History.this"),
      exclude[IncompatibleMethTypeProblem]("sbt.internal.util.complete.Completion.suggestion"),
      exclude[IncompatibleMethTypeProblem]("sbt.internal.util.complete.Completion.token"),
      exclude[IncompatibleMethTypeProblem]("sbt.internal.util.complete.Completion.displayOnly"),
      exclude[IncompatibleSignatureProblem]("sbt.internal.util.complete.*"),
    ),
  )
  .configure(addSbtIO)

// A logic with restricted negation as failure for a unique, stable model
val logicProj = (project in file("internal") / "util-logic")
  .dependsOn(collectionProj, utilRelation)
  .settings(
    testedBaseSettings,
    name := "Logic",
    mimaSettings,
  )

// defines Java structures used across Scala versions, such as the API structures and relationships extracted by
//   the analysis compiler phases and passed back to sbt.  The API structures are defined in a simple
//   format from which Java sources are generated by the datatype generator Projproject
lazy val utilInterface = (project in file("internal") / "util-interface").settings(
  utilCommonSettings,
  crossScalaVersions := List(scala212),
  javaOnlySettings,
  crossPaths := false,
  autoScalaLibrary := false,
  Compile / doc / javacOptions := Nil,
  name := "Util Interface",
  exportJars := true,
  utilMimaSettings,
)

lazy val utilControl = (project in file("internal") / "util-control").settings(
  utilCommonSettings,
  name := "Util Control",
  utilMimaSettings,
)

lazy val utilPosition = (project in file("internal") / "util-position")
  .settings(
    utilCommonSettings,
    name := "Util Position",
    scalacOptions += "-language:experimental.macros",
    libraryDependencies ++= Seq(scalaReflect.value, scalatest % "test"),
    utilMimaSettings,
  )

lazy val utilLogging = (project in file("internal") / "util-logging")
  .enablePlugins(ContrabandPlugin, JsonCodecPlugin)
  .dependsOn(utilInterface, collectionProj, coreMacrosProj)
  .settings(
    utilCommonSettings,
    name := "Util Logging",
    libraryDependencies ++=
      Seq(
        jline,
        jline3Terminal,
        jline3JNA,
        jline3Jansi,
        log4jApi,
        log4jCore,
        disruptor,
        sjsonNewScalaJson.value,
        scalaReflect.value
      ),
    libraryDependencies ++= Seq(scalacheck % "test", scalatest % "test"),
    Compile / scalacOptions ++= (scalaVersion.value match {
      case v if v.startsWith("2.12.") => List("-Ywarn-unused:-locals,-explicits,-privates")
      case _                          => List()
    }),
    Compile / generateContrabands / sourceManaged := baseDirectory.value / "src" / "main" / "contraband-scala",
    Compile / managedSourceDirectories +=
      baseDirectory.value / "src" / "main" / "contraband-scala",
    Compile / generateContrabands / contrabandFormatsForType := { tpe =>
      val old = (Compile / generateContrabands / contrabandFormatsForType).value
      val name = tpe.removeTypeParameters.name
      if (name == "Throwable") Nil
      else old(tpe)
    },
    utilMimaSettings,
    mimaBinaryIssueFilters ++= Seq(
      exclude[DirectMissingMethodProblem]("sbt.internal.util.SuccessEvent.copy*"),
      exclude[DirectMissingMethodProblem]("sbt.internal.util.TraceEvent.copy*"),
      exclude[DirectMissingMethodProblem]("sbt.internal.util.StringEvent.copy*"),
      // Private final class constructors changed
      exclude[DirectMissingMethodProblem]("sbt.util.InterfaceUtil#ConcretePosition.this"),
      exclude[DirectMissingMethodProblem]("sbt.util.InterfaceUtil#ConcreteProblem.this"),
      exclude[ReversedMissingMethodProblem]("sbt.internal.util.ConsoleOut.flush"),
      // This affects Scala 2.11 only it seems, so it's ok?
      exclude[InheritedNewAbstractMethodProblem](
        "sbt.internal.util.codec.JsonProtocol.LogOptionFormat"
      ),
      exclude[InheritedNewAbstractMethodProblem](
        "sbt.internal.util.codec.JsonProtocol.ProgressItemFormat"
      ),
      exclude[InheritedNewAbstractMethodProblem](
        "sbt.internal.util.codec.JsonProtocol.ProgressEventFormat"
      ),
      exclude[DirectMissingMethodProblem]("sbt.internal.util.MainAppender.*"),
      exclude[IncompatibleMethTypeProblem]("sbt.internal.util.BufferedAppender.*"),
      exclude[IncompatibleMethTypeProblem]("sbt.internal.util.ManagedLogger.this"),
      exclude[IncompatibleMethTypeProblem]("sbt.internal.util.ManagedLogger.this"),
      exclude[IncompatibleMethTypeProblem]("sbt.internal.util.MainAppender*"),
      exclude[IncompatibleMethTypeProblem]("sbt.internal.util.GlobalLogging.*"),
      exclude[IncompatibleSignatureProblem]("sbt.internal.util.GlobalLogging.*"),
      exclude[IncompatibleSignatureProblem]("sbt.internal.util.MainAppender*"),
      exclude[MissingTypesProblem]("sbt.internal.util.ConsoleAppender"),
      exclude[MissingTypesProblem]("sbt.internal.util.BufferedAppender"),
      exclude[MissingClassProblem]("sbt.internal.util.Terminal$BlockingInputStream$"),
    ),
  )
  .configure(addSbtIO)

lazy val utilRelation = (project in file("internal") / "util-relation")
  .settings(
    utilCommonSettings,
    name := "Util Relation",
    libraryDependencies ++= Seq(scalacheck % "test"),
    utilMimaSettings,
  )

// Persisted caching based on sjson-new
lazy val utilCache = (project in file("util-cache"))
  .settings(
    utilCommonSettings,
    name := "Util Cache",
    libraryDependencies ++=
      Seq(sjsonNewScalaJson.value, sjsonNewMurmurhash.value, scalaReflect.value),
    libraryDependencies ++= Seq(scalatest % "test"),
    utilMimaSettings,
    mimaBinaryIssueFilters ++= Seq(
      // Added a method to a sealed trait, technically not a problem for Scala
      exclude[ReversedMissingMethodProblem]("sbt.util.HashFileInfo.hashArray"),
    )
  )
  .configure(addSbtIO)

// Builds on cache to provide caching for filesystem-related operations
lazy val utilTracking = (project in file("util-tracking"))
  .dependsOn(utilCache)
  .settings(
    utilCommonSettings,
    name := "Util Tracking",
    libraryDependencies ++= Seq(scalatest % "test"),
    utilMimaSettings,
    mimaBinaryIssueFilters ++= Seq(
      // Private final class constructors changed
      ProblemFilters.exclude[IncompatibleMethTypeProblem]("sbt.util.Tracked#CacheHelp.this"),
    )
  )
  .configure(addSbtIO)

lazy val utilScripted = (project in file("internal") / "util-scripted")
  .dependsOn(utilLogging, utilInterface)
  .settings(
    utilCommonSettings,
    name := "Util Scripted",
    libraryDependencies += scalaParsers.value,
    utilMimaSettings,
  )
  .configure(addSbtIO)
/* **** Intermediate-level Modules **** */

// Runner for uniform test interface
lazy val testingProj = (project in file("testing"))
  .enablePlugins(ContrabandPlugin, JsonCodecPlugin)
  .dependsOn(testAgentProj, utilLogging)
  .settings(
    baseSettings,
    name := "Testing",
    libraryDependencies ++= Seq(
      scalaXml.value,
      testInterface,
      launcherInterface,
      sjsonNewScalaJson.value
    ),
    Compile / scalacOptions += "-Ywarn-unused:-locals,-explicits,-privates",
    Compile / managedSourceDirectories +=
      baseDirectory.value / "src" / "main" / "contraband-scala",
    Compile / generateContrabands / sourceManaged := baseDirectory.value / "src" / "main" / "contraband-scala",
    Compile / generateContrabands / contrabandFormatsForType := ContrabandConfig.getFormats,
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
  .configure(addSbtIO, addSbtCompilerClasspath)

// Testing agent for running tests in a separate process.
lazy val testAgentProj = (project in file("testing") / "agent")
  .settings(
    minimalSettings,
    crossScalaVersions := Seq(baseScalaVersion),
    crossPaths := false,
    autoScalaLibrary := false,
    Compile / doc / javacOptions := Nil,
    name := "Test Agent",
    libraryDependencies += testInterface,
    mimaSettings,
  )

// Basic task engine
lazy val taskProj = (project in file("tasks"))
  .dependsOn(collectionProj, utilControl)
  .settings(
    testedBaseSettings,
    name := "Tasks",
    mimaSettings,
    mimaBinaryIssueFilters ++= Seq(
      exclude[IncompatibleSignatureProblem]("sbt.Triggers.this"),
      exclude[IncompatibleSignatureProblem]("sbt.Triggers.runBefore"),
      exclude[IncompatibleSignatureProblem]("sbt.Triggers.injectFor"),
      exclude[IncompatibleSignatureProblem]("sbt.Triggers.onComplete"),
      exclude[DirectMissingMethodProblem]("sbt.Inc.apply"),
      // ok because sbt.ExecuteProgress has been under private[sbt]
      exclude[IncompatibleResultTypeProblem]("sbt.ExecuteProgress.initial"),
      exclude[DirectMissingMethodProblem]("sbt.ExecuteProgress.*"),
      exclude[ReversedMissingMethodProblem]("sbt.ExecuteProgress.*"),
      exclude[IncompatibleSignatureProblem]("sbt.ExecuteProgress.*"),
      // ok because sbt.Execute has been under private[sbt]
      exclude[IncompatibleSignatureProblem]("sbt.Execute.*"),
      exclude[IncompatibleSignatureProblem]("sbt.Execute#CyclicException.*"),
      exclude[IncompatibleSignatureProblem]("sbt.NodeView.*"),
    )
  )

// Standard task system.  This provides map, flatMap, join, and more on top of the basic task model.
lazy val stdTaskProj = (project in file("tasks-standard"))
  .dependsOn(collectionProj, utilLogging, utilCache)
  .dependsOn(taskProj % "compile;test->test")
  .settings(
    testedBaseSettings,
    name := "Task System",
    testExclusive,
    mimaSettings,
    mimaBinaryIssueFilters ++= Seq(
      // unused private[sbt]
      exclude[DirectMissingMethodProblem]("sbt.Task.mapTask"),
      exclude[NewMixinForwarderProblem]("sbt.std.TaskExtra.joinAnyTasks"),
    ),
  )
  .configure(addSbtIO)

// Embedded Scala code runner
lazy val runProj = (project in file("run"))
  .enablePlugins(ContrabandPlugin)
  .dependsOn(collectionProj, utilLogging, utilControl)
  .settings(
    testedBaseSettings,
    name := "Run",
    Compile / scalacOptions += "-Ywarn-unused:-locals,-explicits,-privates",
    Compile / managedSourceDirectories +=
      baseDirectory.value / "src" / "main" / "contraband-scala",
    Compile / generateContrabands / sourceManaged := baseDirectory.value / "src" / "main" / "contraband-scala",
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
      exclude[Problem]("sbt.TrapExit*"),
      exclude[MissingClassProblem]("sbt.ExitCode"), // private
      exclude[MissingClassProblem]("sbt.LoggingExceptionHandler"), // private
    )
  )
  .configure(addSbtIO, addSbtCompilerClasspath)

val sbtProjDepsCompileScopeFilter =
  ScopeFilter(
    inDependencies(LocalProject("sbtProj"), includeRoot = false),
    inConfigurations(Compile)
  )

lazy val scriptedSbtReduxProj = (project in file("scripted-sbt-redux"))
  .dependsOn(sbtProj % "compile;test->test", commandProj, utilLogging, utilScripted)
  .settings(
    baseSettings,
    crossScalaVersions := Seq(baseScalaVersion),
    name := "Scripted sbt Redux",
    libraryDependencies ++= Seq(launcherInterface % "provided"),
    mimaSettings,
    scriptedSbtReduxMimaSettings,
  )
  .configure(addSbtIO, addSbtCompilerInterface, addSbtLmCore)

lazy val scriptedSbtOldProj = (project in file("scripted-sbt-old"))
  .dependsOn(scriptedSbtReduxProj)
  .settings(
    baseSettings,
    crossScalaVersions := Seq(baseScalaVersion),
    name := "Scripted sbt",
    mimaSettings,
    mimaBinaryIssueFilters ++= Seq(
      // sbt.test package is renamed to sbt.scriptedtest.
      exclude[MissingClassProblem]("sbt.test.*"),
      exclude[DirectMissingMethodProblem]("sbt.test.*"),
      exclude[IncompatibleMethTypeProblem]("sbt.test.*"),
      exclude[IncompatibleSignatureProblem]("sbt.test.*"),
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

lazy val dependencyTreeProj = (project in file("dependency-tree"))
  .dependsOn(sbtProj)
  .settings(
    sbtPlugin := true,
    baseSettings,
    crossScalaVersions := Seq(baseScalaVersion),
    name := "sbt-dependency-tree",
    publishMavenStyle := true,
    // mimaSettings,
    mimaPreviousArtifacts := Set.empty,
  )

// Implementation and support code for defining actions.
lazy val actionsProj = (project in file("main-actions"))
  .dependsOn(
    completeProj,
    runProj,
    stdTaskProj,
    taskProj,
    testingProj,
    utilLogging,
    utilRelation,
    utilTracking,
  )
  .settings(
    testedBaseSettings,
    name := "Actions",
    libraryDependencies += sjsonNewScalaJson.value,
    libraryDependencies += jline3Terminal,
    mimaSettings,
    mimaBinaryIssueFilters ++= Seq(
      // Removed unused private[sbt] nested class
      exclude[MissingClassProblem]("sbt.Doc$Scaladoc"),
      // Removed no longer used private[sbt] method
      exclude[DirectMissingMethodProblem]("sbt.Doc.generate"),
      exclude[DirectMissingMethodProblem]("sbt.compiler.Eval.filesModifiedBytes"),
      exclude[DirectMissingMethodProblem]("sbt.compiler.Eval.fileModifiedBytes"),
      exclude[DirectMissingMethodProblem]("sbt.Doc.$init$"),
      // Added field in nested private[this] class
      exclude[ReversedMissingMethodProblem]("sbt.compiler.Eval#EvalType.sourceName"),
    ),
  )
  .configure(
    addSbtIO,
    addSbtCompilerInterface,
    addSbtCompilerClasspath,
    addSbtCompilerApiInfo,
    addSbtLmCore,
    addSbtZinc
  )

lazy val protocolProj = (project in file("protocol"))
  .enablePlugins(ContrabandPlugin, JsonCodecPlugin)
  .dependsOn(collectionProj, utilLogging)
  .settings(
    testedBaseSettings,
    name := "Protocol",
    libraryDependencies ++= Seq(sjsonNewScalaJson.value, ipcSocket),
    Compile / scalacOptions += "-Ywarn-unused:-locals,-explicits,-privates",
    Compile / managedSourceDirectories +=
      baseDirectory.value / "src" / "main" / "contraband-scala",
    Compile / generateContrabands / sourceManaged := baseDirectory.value / "src" / "main" / "contraband-scala",
    Compile / generateContrabands / contrabandFormatsForType := ContrabandConfig.getFormats,
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
      // ignore missing or incompatible methods in sbt.internal
      exclude[IncompatibleMethTypeProblem]("sbt.internal.*"),
      exclude[DirectMissingMethodProblem]("sbt.internal.*"),
      exclude[MissingTypesProblem]("sbt.internal.protocol.JsonRpcResponseError"),
    )
  )

// General command support and core commands not specific to a build system
lazy val commandProj = (project in file("main-command"))
  .enablePlugins(ContrabandPlugin, JsonCodecPlugin)
  .dependsOn(protocolProj, completeProj, utilLogging)
  .settings(
    testedBaseSettings,
    name := "Command",
    libraryDependencies ++= Seq(launcherInterface, sjsonNewScalaJson.value, templateResolverApi),
    Compile / scalacOptions += "-Ywarn-unused:-locals,-explicits,-privates",
    Compile / managedSourceDirectories +=
      baseDirectory.value / "src" / "main" / "contraband-scala",
    Compile / generateContrabands / sourceManaged := baseDirectory.value / "src" / "main" / "contraband-scala",
    Compile / generateContrabands / contrabandFormatsForType := ContrabandConfig.getFormats,
    mimaSettings,
    mimaBinaryIssueFilters ++= Vector(
      exclude[DirectMissingMethodProblem]("sbt.Exit.apply"),
      exclude[DirectMissingMethodProblem]("sbt.Reboot.apply"),
      exclude[DirectMissingMethodProblem]("sbt.TemplateResolverInfo.apply"),
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
      exclude[MissingTypesProblem]("sbt.internal.server.ServerConnection*"),
      exclude[IncompatibleSignatureProblem]("sbt.internal.server.ServerConnection.*")
    ),
    Compile / headerCreate / unmanagedSources := {
      val old = (Compile / headerCreate / unmanagedSources).value
      old filterNot { x =>
        (x.getName startsWith "NG") || (x.getName == "ReferenceCountedFileDescriptor.java")
      }
    },
  )
  .configure(
    addSbtIO,
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
    baseSettings :+ (crossScalaVersions := (scala212 :: scala213 :: Nil)),
    name := "Core Macros",
    libraryDependencies += {
      if (scalaBinaryVersion.value == "3") {
        "org.scala-lang" % "scala-compiler" % scala213
      } else {
        "org.scala-lang" % "scala-compiler" % scalaVersion.value
      }
    },
    SettingKey[Boolean]("exportPipelining") := false,
    mimaSettings,
  )

// Fixes scope=Scope for Setting (core defined in collectionProj) to define the settings system used in build definitions
lazy val mainSettingsProj = (project in file("main-settings"))
  .dependsOn(
    completeProj,
    commandProj,
    stdTaskProj,
    coreMacrosProj,
    utilLogging,
    utilCache,
    utilRelation,
  )
  .settings(
    testedBaseSettings,
    name := "Main Settings",
    Test / testOptions ++= {
      val cp = (Test / fullClasspathAsJars).value.map(_.data).mkString(java.io.File.pathSeparator)
      val framework = TestFrameworks.ScalaTest
      Tests.Argument(framework, s"-Dsbt.server.classpath=$cp") ::
        Tests.Argument(framework, s"-Dsbt.server.version=${version.value}") ::
        Tests.Argument(framework, s"-Dsbt.server.scala.version=${scalaVersion.value}") :: Nil
    },
    mimaSettings,
    mimaBinaryIssueFilters ++= Seq(
      exclude[IncompatibleSignatureProblem]("sbt.Previous#References.getReferences"),
      exclude[IncompatibleSignatureProblem]("sbt.Def.delegate"),
      exclude[IncompatibleSignatureProblem]("sbt.Def.add"),
      exclude[IncompatibleSignatureProblem]("sbt.Def.grouped"),
      exclude[IncompatibleSignatureProblem]("sbt.Def.compile"),
      exclude[IncompatibleSignatureProblem]("sbt.Def.asTransform"),
      exclude[DirectMissingMethodProblem]("sbt.Def.StaticScopes"),
      exclude[IncompatibleSignatureProblem]("sbt.Previous.this"),
      exclude[DirectMissingMethodProblem]("sbt.BuildRef.apply"),
      exclude[DirectMissingMethodProblem]("sbt.ScopeMask.apply"),
      exclude[DirectMissingMethodProblem]("sbt.Def.intersect"),
      exclude[DirectMissingMethodProblem]("sbt.LocalProject.apply"),
      exclude[DirectMissingMethodProblem]("sbt.std.InitializeInstance.pure"),
      exclude[DirectMissingMethodProblem]("sbt.std.InitializeInstance.flatten"),
      exclude[DirectMissingMethodProblem]("sbt.std.InitializeInstance.map"),
      exclude[DirectMissingMethodProblem]("sbt.std.InitializeInstance.app"),
      exclude[DirectMissingMethodProblem]("sbt.std.ParserInstance.pure"),
      exclude[DirectMissingMethodProblem]("sbt.std.ParserInstance.map"),
      exclude[DirectMissingMethodProblem]("sbt.std.ParserInstance.app"),
      exclude[DirectMissingMethodProblem]("sbt.std.ParserInstance.pure"),
      exclude[DirectMissingMethodProblem]("sbt.std.TaskInstance.pure"),
      exclude[DirectMissingMethodProblem]("sbt.std.TaskInstance.flatten"),
      exclude[DirectMissingMethodProblem]("sbt.std.TaskInstance.map"),
      exclude[DirectMissingMethodProblem]("sbt.std.TaskInstance.app"),
      exclude[DirectMissingMethodProblem]("sbt.std.FullInstance.flatten"),
      exclude[DirectMissingMethodProblem]("sbt.Scope.display012StyleMasked"),
      // added a method to a sealed trait
      exclude[InheritedNewAbstractMethodProblem]("sbt.Scoped.canEqual"),
      exclude[InheritedNewAbstractMethodProblem]("sbt.ScopedTaskable.canEqual"),
      // widened ScopedTaskable parameter to (new) supertype Taskable
      exclude[IncompatibleSignatureProblem]("sbt.Scoped#RichTaskable*.this"),
      exclude[IncompatibleSignatureProblem]("sbt.TupleSyntax.t*ToTable*"),
    ),
  )
  .configure(
    addSbtIO,
    addSbtCompilerInterface,
    addSbtCompilerClasspath,
    addSbtLmCore
  )

lazy val zincLmIntegrationProj = (project in file("zinc-lm-integration"))
  .settings(
    name := "Zinc LM Integration",
    testedBaseSettings,
    Test / testOptions +=
      Tests.Argument(TestFrameworks.ScalaTest, s"-Dsbt.zinc.version=$zincVersion"),
    mimaSettingsSince(sbt13Plus),
    mimaBinaryIssueFilters ++= Seq(
      exclude[IncompatibleMethTypeProblem]("sbt.internal.inc.ZincComponentCompiler*"),
      exclude[IncompatibleSignatureProblem]("sbt.internal.inc.ZincComponentCompiler*"),
      exclude[IncompatibleSignatureProblem]("sbt.internal.inc.ZincLMHelper.update"),
    ),
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
    scriptedPluginProj,
    zincLmIntegrationProj,
    utilLogging,
  )
  .settings(
    testedBaseSettings,
    name := "Main",
    checkPluginCross := {
      val sv = scalaVersion.value
      val f = baseDirectory.value / "src" / "main" / "scala" / "sbt" / "PluginCross.scala"
      if (sv.startsWith("2.12") && !IO.readLines(f).exists(_.contains(s""""$sv""""))) {
        sys.error(s"PluginCross.scala does not match up with the scalaVersion $sv")
      }
    },
    libraryDependencies ++=
      (Seq(scalaXml.value, launcherInterface, caffeine, lmCoursierShaded) ++ log4jModules),
    libraryDependencies ++= (scalaVersion.value match {
      case v if v.startsWith("2.12.") => List()
      case _                          => List(scalaPar)
    }),
    Compile / managedSourceDirectories +=
      baseDirectory.value / "src" / "main" / "contraband-scala",
    Compile / generateContrabands / sourceManaged := baseDirectory.value / "src" / "main" / "contraband-scala",
    Test / testOptions += Tests
      .Argument(TestFrameworks.ScalaCheck, "-minSuccessfulTests", "1000"),
    SettingKey[Boolean]("usePipelining") := false,
    mimaSettings,
    mimaBinaryIssueFilters ++= Vector(
      // New and changed methods on KeyIndex. internal.
      exclude[ReversedMissingMethodProblem]("sbt.internal.KeyIndex.*"),
      // internal
      exclude[IncompatibleMethTypeProblem]("sbt.internal.*"),
      // Changed signature or removed private[sbt] methods
      exclude[DirectMissingMethodProblem]("sbt.Classpaths.unmanagedLibs0"),
      exclude[DirectMissingMethodProblem]("sbt.Defaults.allTestGroupsTask"),
      exclude[DirectMissingMethodProblem]("sbt.Plugins.topologicalSort"),
      exclude[IncompatibleMethTypeProblem]("sbt.Defaults.allTestGroupsTask"),
      exclude[DirectMissingMethodProblem]("sbt.StandardMain.shutdownHook"),
      exclude[DirectMissingMethodProblem]("sbt.nio.Keys.compileBinaryFileInputs"),
      exclude[DirectMissingMethodProblem]("sbt.nio.Keys.compileSourceFileInputs"),
      exclude[MissingClassProblem]("sbt.internal.ResourceLoaderImpl"),
      exclude[IncompatibleSignatureProblem]("sbt.internal.ConfigIndex.*"),
      exclude[IncompatibleSignatureProblem]("sbt.internal.Inspect.*"),
      exclude[IncompatibleSignatureProblem]("sbt.internal.ProjectIndex.*"),
      exclude[IncompatibleSignatureProblem]("sbt.internal.BuildIndex.*"),
      exclude[IncompatibleSignatureProblem]("sbt.internal.server.BuildServerReporter.*"),
      exclude[VirtualStaticMemberProblem]("sbt.internal.server.LanguageServerProtocol.*"),
      exclude[IncompatibleSignatureProblem]("sbt.internal.librarymanagement.IvyXml.*"),
      exclude[IncompatibleSignatureProblem]("sbt.ScriptedPlugin.*Settings"),
      exclude[IncompatibleSignatureProblem]("sbt.plugins.SbtPlugin.*Settings"),
      // Removed private internal classes
      exclude[MissingClassProblem]("sbt.internal.ReverseLookupClassLoaderHolder$BottomClassLoader"),
      exclude[MissingClassProblem](
        "sbt.internal.ReverseLookupClassLoaderHolder$ReverseLookupClassLoader$ResourceLoader"
      ),
      exclude[MissingClassProblem]("sbt.internal.ReverseLookupClassLoaderHolder$ClassLoadingLock"),
      exclude[MissingClassProblem](
        "sbt.internal.ReverseLookupClassLoaderHolder$ReverseLookupClassLoader"
      ),
      exclude[MissingClassProblem]("sbt.internal.LayeredClassLoaderImpl"),
      exclude[MissingClassProblem]("sbt.internal.FileManagement"),
      exclude[MissingClassProblem]("sbt.internal.FileManagement$"),
      exclude[MissingClassProblem]("sbt.internal.FileManagement$CopiedFileTreeRepository"),
      exclude[MissingClassProblem]("sbt.internal.server.LanguageServerReporter*"),
      exclude[MissingClassProblem]("sbt.internal.ExternalHooks"),
      exclude[MissingClassProblem]("sbt.internal.ExternalHooks$"),
      // false positives
      exclude[DirectMissingMethodProblem]("sbt.plugins.IvyPlugin.requires"),
      exclude[DirectMissingMethodProblem]("sbt.plugins.JUnitXmlReportPlugin.requires"),
      exclude[DirectMissingMethodProblem]("sbt.plugins.Giter8TemplatePlugin.requires"),
      exclude[DirectMissingMethodProblem]("sbt.plugins.JvmPlugin.requires"),
      exclude[DirectMissingMethodProblem]("sbt.plugins.SbtPlugin.requires"),
      exclude[DirectMissingMethodProblem]("sbt.ResolvedClasspathDependency.apply"),
      exclude[DirectMissingMethodProblem]("sbt.ClasspathDependency.apply"),
      exclude[IncompatibleSignatureProblem]("sbt.plugins.SemanticdbPlugin.globalSettings"),
      // File -> Source
      exclude[DirectMissingMethodProblem]("sbt.Defaults.cleanFilesTask"),
      exclude[IncompatibleSignatureProblem]("sbt.Defaults.resourceConfigPaths"),
      exclude[IncompatibleSignatureProblem]("sbt.Defaults.sourceConfigPaths"),
      exclude[IncompatibleSignatureProblem]("sbt.Defaults.configPaths"),
      exclude[IncompatibleSignatureProblem]("sbt.Defaults.paths"),
      exclude[IncompatibleSignatureProblem]("sbt.Keys.csrPublications"),
      exclude[IncompatibleSignatureProblem](
        "sbt.coursierint.CoursierArtifactsTasks.coursierPublicationsTask"
      ),
      exclude[IncompatibleSignatureProblem](
        "sbt.coursierint.CoursierArtifactsTasks.coursierPublicationsTask"
      ),
      exclude[IncompatibleSignatureProblem]("sbt.coursierint.LMCoursier.coursierConfiguration"),
      exclude[IncompatibleSignatureProblem]("sbt.coursierint.LMCoursier.publicationsSetting"),
      exclude[IncompatibleSignatureProblem]("sbt.Project.inThisBuild"),
      exclude[IncompatibleSignatureProblem]("sbt.Project.inConfig"),
      exclude[IncompatibleSignatureProblem]("sbt.Project.inTask"),
      exclude[IncompatibleSignatureProblem]("sbt.Project.inScope"),
      exclude[IncompatibleSignatureProblem]("sbt.ProjectExtra.inThisBuild"),
      exclude[IncompatibleSignatureProblem]("sbt.ProjectExtra.inConfig"),
      exclude[IncompatibleSignatureProblem]("sbt.ProjectExtra.inTask"),
      exclude[IncompatibleSignatureProblem]("sbt.ProjectExtra.inScope"),
      exclude[MissingTypesProblem]("sbt.internal.Load*"),
      exclude[IncompatibleSignatureProblem]("sbt.internal.Load*"),
      exclude[MissingTypesProblem]("sbt.internal.server.NetworkChannel"),
      // IvyConfiguration was replaced by InlineIvyConfiguration in the generic
      // signature, this does not break compatibility regardless of what
      // cast a compiler might have inserted based on the old signature
      // since we're returning the same values as before.
      exclude[IncompatibleSignatureProblem]("sbt.Classpaths.mkIvyConfiguration"),
      exclude[IncompatibleMethTypeProblem]("sbt.internal.server.Definition*"),
      exclude[IncompatibleTemplateDefProblem]("sbt.internal.server.LanguageServerProtocol"),
      exclude[DirectMissingMethodProblem]("sbt.Classpaths.warnInsecureProtocol"),
      exclude[DirectMissingMethodProblem]("sbt.Classpaths.warnInsecureProtocolInModules"),
      exclude[MissingClassProblem]("sbt.internal.ExternalHooks*"),
      // This seems to be a mima problem. The older constructor still exists but
      // mima seems to incorrectly miss the secondary constructor that provides
      // the binary compatible version.
      exclude[IncompatibleMethTypeProblem]("sbt.internal.server.NetworkChannel.this"),
      exclude[IncompatibleSignatureProblem]("sbt.internal.DeprecatedContinuous.taskDefinitions"),
      exclude[MissingClassProblem]("sbt.internal.SettingsGraph*"),
      // Tasks include non-Files, but it's ok
      exclude[IncompatibleSignatureProblem]("sbt.Defaults.outputConfigPaths"),
      // private[sbt]
      exclude[DirectMissingMethodProblem]("sbt.Classpaths.trackedExportedProducts"),
      exclude[DirectMissingMethodProblem]("sbt.Classpaths.trackedExportedJarProducts"),
      exclude[DirectMissingMethodProblem]("sbt.Classpaths.unmanagedDependencies0"),
      exclude[DirectMissingMethodProblem]("sbt.Classpaths.internalDependenciesImplTask"),
      exclude[DirectMissingMethodProblem]("sbt.Classpaths.internalDependencyJarsImplTask"),
      exclude[DirectMissingMethodProblem]("sbt.Classpaths.interDependencies"),
      exclude[DirectMissingMethodProblem]("sbt.Classpaths.productsTask"),
      exclude[DirectMissingMethodProblem]("sbt.Classpaths.jarProductsTask"),
      exclude[DirectMissingMethodProblem]("sbt.StandardMain.cache"),
      // internal logging apis,
      exclude[IncompatibleSignatureProblem]("sbt.internal.LogManager*"),
      exclude[MissingTypesProblem]("sbt.internal.RelayAppender"),
      exclude[MissingClassProblem]("sbt.internal.TaskProgress$ProgressThread"),
      // internal implementation
      exclude[MissingClassProblem](
        "sbt.internal.XMainConfiguration$ModifiedConfiguration$ModifiedAppProvider$ModifiedScalaProvider$"
      ),
      // internal impl
      exclude[IncompatibleSignatureProblem]("sbt.internal.Act.configIdent"),
      exclude[IncompatibleSignatureProblem]("sbt.internal.Act.taskAxis"),
      // private[sbt] method, used to call the correct sourcePositionMapper
      exclude[DirectMissingMethodProblem]("sbt.Defaults.foldMappers"),
      exclude[DirectMissingMethodProblem]("sbt.Defaults.toAbsoluteSourceMapper"),
      exclude[DirectMissingMethodProblem]("sbt.Defaults.earlyArtifactPathSetting"),
      exclude[MissingClassProblem]("sbt.internal.server.BuildServerReporter$"),
      exclude[IncompatibleTemplateDefProblem]("sbt.internal.server.BuildServerReporter"),
      exclude[MissingClassProblem]("sbt.internal.CustomHttp*"),
    )
  )
  .configure(
    addSbtIO,
    addSbtLmCore,
    addSbtLmIvy,
    addSbtCompilerInterface,
    addSbtZincCompile
  )

// Strictly for bringing implicits and aliases from subsystems into the top-level sbt namespace through a single package object
//  technically, we need a dependency on all of mainProj's dependencies, but we don't do that since this is strictly an integration project
//  with the sole purpose of providing certain identifiers without qualification (with a package object)
lazy val sbtProj = (project in file("sbt-app"))
  .dependsOn(mainProj)
  .settings(
    testedBaseSettings,
    name := "sbt",
    normalizedName := "sbt",
    version := {
      if (scalaVersion.value == baseScalaVersion) version.value
      else version2_13.value
    },
    crossScalaVersions := Seq(baseScalaVersion),
    crossPaths := false,
    crossTarget := { target.value / scalaVersion.value },
    javaOptions ++= Seq("-Xdebug", "-Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"),
    mimaSettings,
    mimaBinaryIssueFilters ++= sbtIgnoredProblems,
  )
  .settings(
    Test / run / connectInput := true,
    Test / run / outputStrategy := Some(StdoutOutput),
    Test / run / fork := true,
    Test / testOptions ++= {
      val cp = (Test / fullClasspathAsJars).value.map(_.data).mkString(java.io.File.pathSeparator)
      val framework = TestFrameworks.ScalaTest
      Tests.Argument(framework, s"-Dsbt.server.classpath=$cp") ::
        Tests.Argument(framework, s"-Dsbt.server.version=${version.value}") ::
        Tests.Argument(framework, s"-Dsbt.server.scala.version=${scalaVersion.value}") :: Nil
    },
  )
  .configure(addSbtIO, addSbtCompilerBridge)

lazy val serverTestProj = (project in file("server-test"))
  .dependsOn(sbtProj % "compile->test", scriptedSbtReduxProj % "compile->test")
  .settings(
    testedBaseSettings,
    crossScalaVersions := Seq(baseScalaVersion),
    publish / skip := true,
    // make server tests serial
    Test / watchTriggers += baseDirectory.value.toGlob / "src" / "server-test" / **,
    Test / parallelExecution := false,
    Test / run / connectInput := true,
    Test / run / outputStrategy := Some(StdoutOutput),
    Test / run / fork := true,
    Test / sourceGenerators += Def.task {
      val rawClasspath =
        (Compile / fullClasspathAsJars).value.map(_.data).mkString(java.io.File.pathSeparator)
      val cp =
        if (scala.util.Properties.isWin) rawClasspath.replace("\\", "\\\\")
        else rawClasspath
      val content = {
        s"""|
            |package testpkg
            |
            |object TestProperties {
            |  val classpath = "$cp"
            |  val version = "${version.value}"
            |  val scalaVersion = "${scalaVersion.value}"
            |}
          """.stripMargin
      }
      val file = (Test / target).value / "generated" / "src" / "test" / "scala" / "testpkg" / "TestProperties.scala"
      IO.write(file, content)
      file :: Nil
    },
  )

val isWin = scala.util.Properties.isWin
val isLinux = scala.util.Properties.isLinux
val isArmArchitecture: Boolean = sys.props
  .getOrElse("os.arch", "")
  .toLowerCase(Locale.ROOT) == "aarch64"
val buildThinClient =
  inputKey[JPath]("generate a java implementation of the thin client")
// Use a TaskKey rather than SettingKey for nativeInstallDirectory so it can left unset by default
val nativeInstallDirectory = taskKey[JPath]("The install directory for the native executable")
val installNativeThinClient = inputKey[JPath]("Install the native executable")
lazy val sbtClientProj = (project in file("client"))
  .enablePlugins(NativeImagePlugin)
  .dependsOn(commandProj)
  .settings(
    commonBaseSettings,
    scalaVersion := "2.12.11", // The thin client does not build with 2.12.12
    publish / skip := true,
    name := "sbt-client",
    mimaPreviousArtifacts := Set.empty,
    crossPaths := false,
    exportJars := true,
    libraryDependencies += jansi,
    libraryDependencies += scalatest % Test,
    Compile / mainClass := Some("sbt.client.Client"),
    nativeImageReady := { () =>
      ()
    },
    nativeImageOutput := target.value / "bin" / "sbtn",
    nativeImageOptions ++= Seq(
      "--no-fallback",
      s"--initialize-at-run-time=sbt.client",
      // "--verbose",
      "-H:IncludeResourceBundles=jline.console.completer.CandidateListCompletionHandler",
      "-H:+ReportExceptionStackTraces",
      "-H:-ParseRuntimeOptions",
      s"-H:Name=${target.value / "bin" / "sbtn"}",
    ) ++ (if (isLinux && isArmArchitecture)
            Seq("-H:PageSize=65536") // Make sure binary runs on kernels with page size set to 4k, 16 and 64k
          else Nil),
    buildThinClient := {
      val isFish = Def.spaceDelimited("").parsed.headOption.fold(false)(_ == "--fish")
      val ext = if (isWin) ".bat" else if (isFish) ".fish" else ".sh"
      val output = target.value.toPath / "bin" / s"${if (isFish) "fish-" else ""}client$ext"
      java.nio.file.Files.createDirectories(output.getParent)
      val cp = (Compile / fullClasspathAsJars).value.map(_.data)
      val args =
        if (isWin) "%*" else if (isFish) s"$$argv" else s"$$*"
      java.nio.file.Files.write(
        output,
        s"""
        |${if (isWin) "@echo off" else s"#!/usr/bin/env ${if (isFish) "fish" else "sh"}"}
        |
        |java -cp ${cp.mkString(java.io.File.pathSeparator)} sbt.client.Client --jna $args
        """.stripMargin.linesIterator.toSeq.tail.mkString("\n").getBytes
      )
      output.toFile.setExecutable(true)
      output
    },
  )

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

// util projects used by Zinc and Lm
lazy val lowerUtils = (project in (file("internal") / "lower"))
  .aggregate(lowerUtilProjects.map(p => LocalProject(p.id)): _*)
  .settings(
    publish / skip := true,
    crossScalaVersions := Nil,
  )

lazy val upperModules = (project in (file("internal") / "upper"))
  .aggregate(
    ((allProjects diff lowerUtilProjects)
      diff Seq(bundledLauncherProj)).map(p => LocalProject(p.id)): _*
  )
  .settings(
    publish / skip := true,
    crossScalaVersions := Nil,
  )

lazy val sbtIgnoredProblems = {
  Vector(
    exclude[IncompatibleSignatureProblem]("sbt.package.some"),
    exclude[IncompatibleSignatureProblem]("sbt.package.inThisBuild"),
    exclude[IncompatibleSignatureProblem]("sbt.package.inConfig"),
    exclude[IncompatibleSignatureProblem]("sbt.package.inTask"),
    exclude[IncompatibleSignatureProblem]("sbt.package.inScope"),
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
    exclude[NewMixinForwarderProblem]("sbt.IOSyntax1.singleFileFinder"),
    exclude[DirectMissingMethodProblem]("sbt.IOSyntax1.$init$"),
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
    bspEnabled := false,
    crossPaths := false,
    crossScalaVersions := Seq(baseScalaVersion),
    publish / skip := true,
    Compile / compile := {
      val _ = update.value
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

def scriptedTask(launch: Boolean): Def.Initialize[InputTask[Unit]] = Def.inputTask {
  val _ = publishLocalBinAll.value
  val launchJar = s"-Dsbt.launch.jar=${(bundledLauncherProj / Compile / packageBin).value}"
  Scripted.doScripted(
    (scriptedSbtReduxProj / scalaInstance).value,
    scriptedSource.value,
    scriptedBufferLog.value,
    Def.setting(Scripted.scriptedParser(scriptedSource.value)).parsed,
    scriptedPrescripted.value,
    scriptedLaunchOpts.value ++ (if (launch) Some(launchJar) else None),
    scalaVersion.value,
    version.value,
    (scriptedSbtReduxProj / Test / fullClasspathAsJars).value
      .map(_.data)
      .filterNot(_.getName.contains("scala-compiler")),
    streams.value.log
  )
}

lazy val publishLauncher = TaskKey[Unit]("publish-launcher")

def allProjects =
  Seq(
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
    dependencyTreeProj,
    protocolProj,
    actionsProj,
    commandProj,
    mainSettingsProj,
    zincLmIntegrationProj,
    mainProj,
    sbtProj,
    bundledLauncherProj,
    sbtClientProj,
  ) ++ lowerUtilProjects

// These need to be cross published to 2.12 and 2.13 for Zinc
lazy val lowerUtilProjects =
  Seq(
    collectionProj,
    coreMacrosProj,
    utilCache,
    utilControl,
    utilInterface,
    utilLogging,
    utilPosition,
    utilRelation,
    utilScripted,
    utilTracking
  )

lazy val nonRoots = allProjects.map(p => LocalProject(p.id))

ThisBuild / scriptedBufferLog := true
ThisBuild / scriptedPrescripted := { _ =>
}

def otherRootSettings =
  Seq(
    scripted := scriptedTask(false).evaluated,
    scriptedUnpublished := scriptedTask(false).evaluated,
    scriptedSource := (sbtProj / sourceDirectory).value / "sbt-test",
    scripted / watchTriggers += scriptedSource.value.toGlob / **,
    scriptedUnpublished / watchTriggers := (scripted / watchTriggers).value,
    scriptedLaunchOpts := List("-Xmx1500M", "-Xms512M", "-server") :::
      (sys.props.get("sbt.ivy.home") match {
        case Some(home) => List(s"-Dsbt.ivy.home=$home")
        case _          => Nil
      }),
    publishLocalBinAll := {
      val _ = (Compile / publishLocalBin).all(scriptedProjects).value
    },
  ) ++ inConfig(Scripted.RepoOverrideTest)(
    Seq(
      scriptedLaunchOpts := List(
        "-Xmx1500M",
        "-Xms512M",
        "-server",
        "-Dsbt.override.build.repos=true",
        s"""-Dsbt.repository.config=${scriptedSource.value / "repo.config"}"""
      ) :::
        (sys.props.get("sbt.ivy.home") match {
          case Some(home) => List(s"-Dsbt.ivy.home=$home")
          case _          => Nil
        }),
      scripted := scriptedTask(true).evaluated,
      scriptedUnpublished := scriptedTask(true).evaluated,
      scriptedSource := (sbtProj / sourceDirectory).value / "repo-override-test"
    )
  )

lazy val docProjects: ScopeFilter = ScopeFilter(
  inAnyProject -- inProjects(
    sbtRoot,
    sbtProj,
    scriptedSbtReduxProj,
    scriptedSbtOldProj,
    scriptedPluginProj,
    upperModules,
    lowerUtils,
  ),
  inConfigurations(Compile)
)
lazy val javafmtOnCompile = taskKey[Unit]("Formats java sources before compile")
lazy val scriptedProjects = ScopeFilter(inAnyProject -- inProjects(vscodePlugin))

def customCommands: Seq[Setting[_]] = Seq(
  commands += Command.command("setupBuildScala212") { state =>
    s"""set scalaVersion in ThisBuild := "$scala212" """ ::
      state
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
          val zincSv = get((ProjectRef(build, "zinc") / scalaVersion))
          val csv = get((ProjectRef(build, "compilerBridge") / crossScalaVersions)).toList
          (csv flatMap { bridgeSv =>
            s"++$bridgeSv" :: ("{" + build.toString + "}compilerBridge/publishLocal") :: Nil
          }) :::
            List(s"++$zincSv", "{" + build.toString + "}/publishLocal")
      }).getOrElse(Nil) :::
      List(s"++$sv", "publishLocal") :::
      state
  },
  commands += Command.command("releaseLowerUtils") { state =>
    // TODO - Any sort of validation
    "clean" ::
      "+lowerUtils/compile" ::
      "+lowerUtils/publishSigned" ::
      s"++$scala212" ::
      state
  },
  commands += Command.command("release") { state =>
    // TODO - Any sort of validation
    "upperModules/compile" ::
      "upperModules/publishSigned" ::
      "bundledLauncherProj/publishSigned" ::
      state
  },
)

ThisBuild / pomIncludeRepository := { _ =>
  false
}
ThisBuild / publishTo := {
  val nexus = "https://oss.sonatype.org/"
  Some("releases" at nexus + "service/local/staging/deploy/maven2")
}
ThisBuild / publishMavenStyle := true
