import Dependencies.*
import com.typesafe.tools.mima.core.ProblemFilters.*
import com.typesafe.tools.mima.core.*
import local.Scripted
import java.nio.file.{ Files, Path => JPath }
import java.util.Locale
import sbt.internal.inc.Analysis
import sbt.Tags
import com.eed3si9n.jarjarabrams.ModuleCoordinate

// ThisBuild settings take lower precedence,
// but can be shared across the multi projects.
ThisBuild / version := {
  val v = "2.0.0-RC9-bin-SNAPSHOT"
  nightlyVersion.getOrElse(v)
}
ThisBuild / versionScheme := Some("early-semver")
ThisBuild / scalafmtOnCompile := !(Global / insideCI).value
ThisBuild / Test / scalafmtOnCompile := !(Global / insideCI).value
// ThisBuild / turbo := true
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
ThisBuild / mimaFailOnNoPrevious := false
ThisBuild / scalafixOnCompile := true

Global / semanticdbEnabled := !(Global / insideCI).value
// Change main/src/main/scala/sbt/plugins/SemanticdbPlugin.scala too, if you change this.
Global / semanticdbVersion := "4.15.2"
val excludeLint = SettingKey[Set[Def.KeyedInitialize[?]]]("excludeLintKeys")
Global / excludeLint := (Global / excludeLint).?.value.getOrElse(Set.empty)
Global / excludeLint += Utils.componentID
Global / excludeLint += scriptedBufferLog
Global / excludeLint += nativeImageJvm
Global / excludeLint += nativeImageVersion

usePgpKeyHex("642AC823")
// usePgpKeyHex("2BE67AC00D699E04E840B7FE29967E804D85663F")

def commonSettings: Seq[Setting[?]] = Def.settings(
  headerLicense := Some(
    HeaderLicense.Custom(
      """|sbt
       |Copyright 2023, Scala center
       |Copyright 2011 - 2022, Lightbend, Inc.
       |Copyright 2008 - 2010, Mark Harrah
       |Licensed under Apache License 2.0 (see LICENSE)
       |""".stripMargin
    )
  ),
  scalaVersion := baseScalaVersion,
  evictionErrorLevel := Level.Info,
  Utils.componentID := None,
  resolvers += Resolver.sonatypeCentralSnapshots,
  testFrameworks += TestFramework("hedgehog.sbt.Framework"),
  testFrameworks += TestFramework("verify.runner.Framework"),
  Global / concurrentRestrictions += Utils.testExclusiveRestriction,
  // On Windows, limit to one task at a time to avoid OverlappingFileLockException when
  // multiple tasks (e.g. scalafix plugin and sbt Coursier) write to the same cache.
  Global / concurrentRestrictions ++= (if (scala.util.Properties.isWin) Seq(Tags.limitAll(1))
                                       else Nil),
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
  Compile / javafmtOnCompile := scalafmtOnCompile.value,
  Test / javafmtOnCompile := (Test / scalafmtOnCompile).value,
  Compile / unmanagedSources / inputFileStamps :=
    (Compile / unmanagedSources / inputFileStamps).dependsOn(Compile / javafmt).value,
  Test / unmanagedSources / inputFileStamps :=
    (Test / unmanagedSources / inputFileStamps).dependsOn(Test / javafmt).value,
  Test / publishArtifact := false,
  run / fork := true,
)

def utilCommonSettings: Seq[Setting[?]] = Def.settings(
  baseSettings,
)

def minimalSettings: Seq[Setting[?]] =
  commonSettings ++ customCommands ++ Utils.publishPomSettings

def baseSettings: Seq[Setting[?]] =
  minimalSettings ++ Seq(Utils.projectComponent) ++ Utils.baseScalacOptions ++ Licensed.settings

def testedBaseSettings: Seq[Setting[?]] =
  baseSettings ++ testDependencies

val sbt20Plus =
  Seq(
    "2.0.0-RC11",
  )
val mimaSettings = mimaSettingsSince(sbt20Plus)
def mimaSettingsSince(versions: Seq[String]): Seq[Def.Setting[?]] = Def settings (
  mimaPreviousArtifacts := {
    val crossVersion = if (crossPaths.value) CrossVersion.binary else CrossVersion.disabled
    if (sbtPlugin.value) {
      versions
        .map(v =>
          Defaults.sbtPluginExtra(
            m = organization.value % moduleName.value % v,
            sbtV = "2",
            scalaV = scalaBinaryVersion.value
          )
        )
        .toSet
    } else {
      versions.map(v => organization.value % moduleName.value % v cross crossVersion).toSet
    }
  },
  mimaBinaryIssueFilters ++= Seq(
  ),
)

val contrabandSettings: Seq[Def.Setting[?]] = Seq(
  Compile / generateContrabands / sourceManaged := baseDirectory.value / "src" / "main" / "contraband-scala",
  Compile / managedSourceDirectories +=
    baseDirectory.value / "src" / "main" / "contraband-scala",
  Compile / generateContrabands / contrabandScala3enum := false,
  Compile / generateContrabands / contrabandFormatsForType := DatatypeConfig.getFormats,
)

val scriptedSbtMimaSettings = Def.settings(mimaPreviousArtifacts := Set())

lazy val sbtRoot: Project = (project in file("."))
  .aggregate(
    (allProjects diff Seq(lmCoursierShaded))
      .map(p => LocalProject(p.id))*
  )
  .settings(
    Seq(scalafmtSbt, scalafmtSbtCheck).map { formatTask =>
      Compile / formatTask := {
        (Compile / formatTask).value
        (launcherPackage / Compile / formatTask).value
      }
    },
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
        (if (version != "17")
           s"""!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
               |  Java version is $version. We recommend java 17.
               |!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!""".stripMargin
         else "")
    },
    Utils.baseScalacOptions,
    Docs.settings,
    scalacOptions += "-Ymacro-expand:none", // for both sxr and doc
    Utils.publishPomSettings,
    otherRootSettings,
    Utils.noPublish,
    publishLocal := {},
    Global / commands += Command
      .single("sbtOn")((state, dir) => s"sbtProj/Test/runMain sbt.RunFromSourceMain $dir" :: state),
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

val collectionProj = project
  .in(file("util-collection"))
  .dependsOn(utilPosition, utilCore)
  .settings(
    name := "Collections",
    testedBaseSettings,
    libraryDependencies ++= Seq(sjsonNewScalaJson.value),
    libraryDependencies ++= Seq(scalaPar),
    mimaSettings,
    conflictWarning := ConflictWarning.disable,
  )

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
    // mimaBinaryIssueFilters := Seq(),
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
  baseSettings,
  Utils.javaOnlySettings,
  crossPaths := false,
  autoScalaLibrary := false,
  Compile / doc / javacOptions := Nil,
  name := "Util Interface",
  exportJars := true,
  mimaSettings,
)

lazy val utilControl = (project in file("internal") / "util-control").settings(
  utilCommonSettings,
  name := "Util Control",
  mimaSettings,
)

lazy val utilPosition = (project in file("internal") / "util-position")
  .settings(
    utilCommonSettings,
    name := "Util Position",
    scalacOptions += "-language:experimental.macros",
    libraryDependencies ++= Seq(hedgehog % Test),
    mimaSettings,
    mimaBinaryIssueFilters ++= Seq(
    ),
  )

lazy val utilCore = project
  .in(file("internal") / "util-core")
  .settings(
    utilCommonSettings,
    name := "Util Core",
    Utils.keywordsSettings,
    libraryDependencies ++= Seq(hedgehog % Test),
    mimaSettings,
    mimaBinaryIssueFilters ++= Seq(
    ),
  )

lazy val utilLogging = project
  .in(file("internal") / "util-logging")
  .enablePlugins(ContrabandPlugin, JsonCodecPlugin)
  .dependsOn(utilInterface, utilCore)
  .settings(
    utilCommonSettings,
    name := "Util Logging",
    libraryDependencies ++=
      Seq(
        jline,
        jline3Terminal,
        jline3JNI,
        jline3Native,
        disruptor,
        sjsonNewScalaJson.value,
      ),
    testDependencies,
    contrabandSettings,
    Compile / generateContrabands / contrabandFormatsForType := { tpe =>
      val old = (Compile / generateContrabands / contrabandFormatsForType).value
      val name = tpe.removeTypeParameters.name
      if (name == "Throwable") Nil
      else old(tpe)
    },
    Test / fork := true,
    mimaSettings,
    mimaBinaryIssueFilters ++= Seq(
      ProblemFilters.exclude[MissingClassProblem]("com.github.ghik.silencer.silent")
    ),
  )
  .configure(addSbtIO)

lazy val utilRelation = (project in file("internal") / "util-relation")
  .settings(
    utilCommonSettings,
    name := "Util Relation",
    libraryDependencies ++= Seq(scalacheck % "test"),
    mimaSettings,
  )

// Persisted caching based on sjson-new
lazy val utilCache = project
  .in(file("util-cache"))
  .enablePlugins(
    ContrabandPlugin,
    // we generate JsonCodec only for actionresult.contra
    JsonCodecPlugin,
  )
  .dependsOn(utilLogging)
  .settings(
    testedBaseSettings,
    name := "Util Cache",
    libraryDependencies ++=
      Seq(
        caffeine,
        sjsonNewCore.value,
        sjsonNewScalaJson.value,
        sjsonNewMurmurhash.value
      ),
    contrabandSettings,
    mimaSettings,
    mimaBinaryIssueFilters ++= Seq(
    ),
    Test / fork := true,
  )
  .configure(
    addSbtIO,
    addSbtCompilerInterface,
  )

// Builds on cache to provide caching for filesystem-related operations
lazy val utilTracking = (project in file("util-tracking"))
  .dependsOn(utilCache)
  .settings(
    utilCommonSettings,
    name := "Util Tracking",
    libraryDependencies ++= Seq(
      scalacheck % Test,
      scalaVerify % Test,
      hedgehog % Test,
    ),
    mimaSettings,
    mimaBinaryIssueFilters ++= Seq(
    )
  )
  .configure(addSbtIO)

lazy val utilScripted = (project in file("internal") / "util-scripted")
  .dependsOn(utilLogging, utilInterface)
  .settings(
    utilCommonSettings,
    name := "Util Scripted",
    libraryDependencies += scalaParsers,
    mimaSettings,
  )
  .configure(addSbtIO)
/* **** Intermediate-level Modules **** */

// Runner for uniform test interface
lazy val testingProj = (project in file("testing"))
  .enablePlugins(ContrabandPlugin, JsonCodecPlugin)
  .dependsOn(workerProj, utilLogging)
  .settings(
    baseSettings,
    name := "Testing",
    libraryDependencies ++= Seq(
      scalaXml,
      testInterface,
      launcherInterface,
      sjsonNewScalaJson.value,
      sjsonNewCore.value,
      scalaVerify % Test,
    ),
    testFrameworks += TestFramework("verify.runner.Framework"),
    conflictWarning := ConflictWarning.disable,
    contrabandSettings,
    mimaSettings,
    mimaBinaryIssueFilters ++= Vector(
    ),
  )
  .configure(addSbtIO, addSbtCompilerClasspath)

lazy val workerProj = (project in file("worker"))
  .dependsOn(exampleWorkProj % Test)
  .settings(
    name := "worker",
    testedBaseSettings,
    Compile / doc / javacOptions := Nil,
    crossPaths := false,
    autoScalaLibrary := false,
    libraryDependencies ++= Seq(gson, testInterface),
    libraryDependencies += "org.scala-lang" %% "scala3-library" % scalaVersion.value % Test,
    // run / fork := false,
    Test / fork := true,
    mimaSettings,
    mimaBinaryIssueFilters ++= Vector(
    ),
  )
  .configure(
    addSbtCompilerInterface,
    addSbtIOForTest
  )

lazy val exampleWorkProj = (project in file("internal") / "example-work")
  .settings(
    minimalSettings,
    name := "example work",
    publish / skip := true,
  )

// Basic task engine
lazy val taskProj = (project in file("tasks"))
  .dependsOn(collectionProj, utilControl)
  .settings(
    testedBaseSettings,
    name := "Tasks",
    mimaSettings,
    mimaBinaryIssueFilters ++= Seq(
    )
  )

// Standard task system.  This provides map, flatMap, join, and more on top of the basic task model.
lazy val stdTaskProj = (project in file("tasks-standard"))
  .dependsOn(collectionProj, utilLogging, utilCache)
  .dependsOn(taskProj % "compile;test->test")
  .settings(
    testedBaseSettings,
    name := "Task System",
    Utils.testExclusive,
    mimaSettings,
    mimaBinaryIssueFilters ++= Seq(
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
    contrabandSettings,
    mimaSettings,
    mimaBinaryIssueFilters ++= Seq(
    )
  )
  .configure(addSbtIO, addSbtCompilerClasspath)

val sbtProjDepsCompileScopeFilter =
  ScopeFilter(
    inDependencies(LocalProject("sbtProj"), includeRoot = false),
    inConfigurations(Compile)
  )

lazy val scriptedSbtProj = (project in file("scripted-sbt"))
  .dependsOn(sbtProj % "compile;test->test", commandProj, utilLogging, utilScripted)
  .settings(
    baseSettings,
    name := "scripted-sbt",
    libraryDependencies ++= Seq(launcherInterface % "provided"),
    mimaSettings,
    mimaBinaryIssueFilters ++= Seq(
    ),
  )
  .dependsOn(lmCore)
  .configure(addSbtIO, addSbtCompilerInterface)

lazy val remoteCacheProj = (project in file("sbt-remote-cache"))
  .dependsOn(sbtProj)
  .settings(
    sbtPlugin := true,
    baseSettings,
    name := "sbt-remote-cache",
    pluginCrossBuild / sbtVersion := version.value,
    publishMavenStyle := true,
    mimaSettings,
    libraryDependencies += remoteapis,
  )

// Implementation and support code for defining actions.
lazy val actionsProj = (project in file("main-actions"))
  .enablePlugins(ContrabandPlugin, JsonCodecPlugin)
  .dependsOn(
    completeProj,
    runProj,
    stdTaskProj,
    taskProj,
    testingProj,
    utilCache,
    utilLogging,
    utilRelation,
    utilTracking,
    workerProj,
    protocolProj,
  )
  .settings(
    testedBaseSettings,
    name := "Actions",
    libraryDependencies += sjsonNewScalaJson.value,
    libraryDependencies ++= Seq(gigahorseApacheHttp, jline3Terminal),
    contrabandSettings,
    // Test / fork := true,
    Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat,
    mimaSettings,
    mimaBinaryIssueFilters ++= Vector(
    ),
  )
  .dependsOn(lmCore)
  .configure(
    addSbtIO,
    addSbtCompilerInterface,
    addSbtCompilerClasspath,
    addSbtCompilerApiInfo,
    addSbtZinc
  )

lazy val protocolProj = (project in file("protocol"))
  .enablePlugins(ContrabandPlugin, JsonCodecPlugin)
  .dependsOn(collectionProj, utilLogging)
  .settings(
    testedBaseSettings,
    name := "Protocol",
    libraryDependencies ++= Seq(sjsonNewScalaJson.value, sjsonNewCore.value, ipcSocket),
    Compile / scalacOptions += "-source:3.7",
    contrabandSettings,
    mimaSettings,
    mimaBinaryIssueFilters ++= Seq(
    )
  )

// General command support and core commands not specific to a build system
lazy val commandProj = (project in file("main-command"))
  .enablePlugins(ContrabandPlugin, JsonCodecPlugin)
  .dependsOn(protocolProj, completeProj, utilLogging, runProj, utilCache)
  .settings(
    testedBaseSettings,
    name := "Command",
    libraryDependencies ++= Seq(
      launcherInterface,
      sjsonNewCore.value,
      sjsonNewScalaJson.value,
      templateResolverApi
    ),
    contrabandSettings,
    mimaSettings,
    mimaBinaryIssueFilters ++= Vector(
      exclude[MissingClassProblem]("sbt.internal.util.JoinThread"),
      exclude[MissingClassProblem]("sbt.internal.util.JoinThread$"),
      exclude[MissingClassProblem]("sbt.internal.util.ReadJsonFromInputStream"),
      exclude[MissingClassProblem]("sbt.internal.util.ReadJsonFromInputStream$"),
      exclude[MissingClassProblem]("sbt.internal.client.ServerConnection"),
      exclude[IncompatibleResultTypeProblem]("sbt.internal.client.NetworkClient.connection"),
      exclude[IncompatibleResultTypeProblem]("sbt.internal.client.NetworkClient.init")
    ),
    Compile / headerCreate / unmanagedSources := {
      val old = (Compile / headerCreate / unmanagedSources).value
      old filterNot { x =>
        (x.getName startsWith "NG") || (x.getName == "ReferenceCountedFileDescriptor.java")
      }
    },
  )
  .dependsOn(lmCore)
  .configure(
    addSbtIO,
    addSbtCompilerInterface,
    addSbtCompilerClasspath,
    addSbtZinc
  )

// The core macro project defines the main logic of the DSL, abstracted
// away from several sbt implementors (tasks, settings, et cetera).
lazy val coreMacrosProj = (project in file("core-macros"))
  .dependsOn(
    collectionProj,
    utilCache,
  )
  .settings(
    testedBaseSettings,
    name := "Core Macros",
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
    logicProj,
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
    ),
  )
  .dependsOn(lmCore)
  .configure(addSbtIO, addSbtCompilerInterface, addSbtCompilerClasspath)

lazy val zincLmIntegrationProj = (project in file("zinc-lm-integration"))
  .settings(
    name := "Zinc LM Integration",
    testedBaseSettings,
    Test / testOptions +=
      Tests.Argument(TestFrameworks.ScalaTest, s"-Dsbt.zinc.version=$zincVersion"),
    mimaSettings,
    mimaBinaryIssueFilters ++= Seq(
    ),
    libraryDependencies += launcherInterface,
  )
  .dependsOn(lmCore, lmIvy)
  .configure(addSbtZincCompileCore)

lazy val buildFileProj = (project in file("buildfile"))
  .dependsOn(
    mainSettingsProj,
  )
  .settings(
    testedBaseSettings,
    name := "build file",
    libraryDependencies ++= Seq(scalaCompiler),
    mimaSettings,
  )
  .dependsOn(lmCore)
  .configure(addSbtIO, addSbtCompilerInterface, addSbtZincCompileCore)

// The main integration project for sbt.  It brings all of the projects together, configures them, and provides for overriding conventions.
lazy val mainProj = (project in file("main"))
  .enablePlugins(ContrabandPlugin)
  .dependsOn(
    actionsProj,
    buildFileProj,
    mainSettingsProj,
    runProj,
    commandProj,
    collectionProj,
    zincLmIntegrationProj,
    utilLogging,
  )
  .settings(
    testedBaseSettings,
    name := "Main",
    Compile / sourceGenerators += task {
      val f = (Compile / sourceManaged).value / "sbt" / "PluginCrossExtra.scala"
      IO.write(
        f,
        s"""|package sbt
            |
            |private[sbt] trait PluginCrossExtra { self: PluginCross.type =>
            |  def scala3: String = "${Dependencies.scala3}"
            |}
            |""".stripMargin
      )
      Seq(f)
    },
    libraryDependencies ++=
      Seq(
        scalaXml,
        sjsonNewScalaJson.value,
        sjsonNewCore.value,
        launcherInterface,
        caffeine,
        scala3Library,
        scalaCollectionCompat,
      ),
    libraryDependencies ++= List(scalaPar),
    contrabandSettings,
    Test / testOptions += Tests
      .Argument(TestFrameworks.ScalaCheck, "-minSuccessfulTests", "1000"),
    SettingKey[Boolean]("usePipelining") := false,
    // TODO: Fix doc
    Compile / doc / sources := Nil,
    mimaSettings,
    mimaBinaryIssueFilters ++= Vector(
      // Moved to sbt-ivy module (Step 5 of sbt#7640)
      exclude[DirectMissingMethodProblem]("sbt.Classpaths.depMap"),
      exclude[DirectMissingMethodProblem]("sbt.Classpaths.ivySbt0"),
      exclude[DirectMissingMethodProblem]("sbt.Classpaths.mkIvyConfiguration"),
      exclude[MissingClassProblem]("sbt.internal.librarymanagement.IvyXml"),
      exclude[MissingClassProblem]("sbt.internal.librarymanagement.IvyXml$"),
      // Removed projectDescriptors key (sbt#8865)
      exclude[DirectMissingMethodProblem]("sbt.Keys.projectDescriptors"),
      // Removed descriptors field from GlobalPluginData (sbt#8865)
      exclude[DirectMissingMethodProblem]("sbt.internal.GlobalPluginData.apply"),
      exclude[DirectMissingMethodProblem]("sbt.internal.GlobalPluginData.this"),
      exclude[DirectMissingMethodProblem]("sbt.internal.GlobalPluginData.descriptors"),
      exclude[DirectMissingMethodProblem]("sbt.internal.GlobalPluginData.copy"),
      exclude[IncompatibleResultTypeProblem]("sbt.internal.GlobalPluginData.copy$default$3"),
      exclude[IncompatibleResultTypeProblem]("sbt.internal.GlobalPluginData.copy$default$4"),
      exclude[DirectMissingMethodProblem]("sbt.internal.GlobalPluginData.copy$default$6"),
      exclude[IncompatibleResultTypeProblem]("sbt.internal.GlobalPluginData._3"),
      exclude[IncompatibleResultTypeProblem]("sbt.internal.GlobalPluginData._4"),
      exclude[DirectMissingMethodProblem]("sbt.internal.GlobalPluginData._6"),
      // Updating remote vcs projects (sbt#1284)
      exclude[DirectMissingMethodProblem]("sbt.Resolvers.creates"),
      exclude[DirectMissingMethodProblem]("sbt.Resolvers.uniqueSubdirectoryFor"),
      exclude[DirectMissingMethodProblem]("sbt.Resolvers.run"),
      exclude[MissingClassProblem]("sbt.Resolvers$DistributedVCS")
    ),
  )
  .dependsOn(lmCore, lmCoursierShadedPublishing)
  .configure(addSbtIO, addSbtCompilerInterface, addSbtZincCompileCore)

lazy val sbtIvyProj = (project in file("sbt-ivy"))
  .dependsOn(sbtProj, lmIvy)
  .settings(
    testedBaseSettings,
    name := "sbt-ivy",
    sbtPlugin := true,
    pluginCrossBuild / sbtVersion := version.value,
    // TODO: Fix doc
    Compile / doc / sources := Nil,
    mimaPreviousArtifacts := Set.empty, // new module, no previous artifacts
  )
  .configure(addSbtIO)

// Strictly for bringing implicits and aliases from subsystems into the top-level sbt namespace through a single package object
//  technically, we need a dependency on all of mainProj's dependencies, but we don't do that since this is strictly an integration project
//  with the sole purpose of providing certain identifiers without qualification (with a package object)
lazy val sbtProj = (project in file("sbt-app"))
  .dependsOn(mainProj)
  .settings(
    testedBaseSettings,
    name := "sbt",
    normalizedName := "sbt",
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
  .configure(addSbtIO)
// addSbtCompilerBridge

lazy val serverTestProj = (project in file("server-test"))
  .dependsOn(sbtProj % "compile->test", scriptedSbtProj % "compile->test")
  .settings(
    testedBaseSettings,
    allowUnsafeScalaLibUpgrade := true, // demote Scala 3 eviction to warn if deps bring newer scala3-library_3
    Utils.noPublish,
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
      val file =
        (Test / target).value / "generated" / "src" / "test" / "scala" / "testpkg" / "TestProperties.scala"
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
    commonSettings,
    Utils.noPublish,
    name := "sbt-client",
    bspEnabled := false,
    crossPaths := false,
    exportJars := true,
    libraryDependencies ++= scalatest,
    Compile / mainClass := Some("sbt.client.Client"),
    nativeImageReady := { () =>
      ()
    },
    nativeImageVersion := "23.0",
    nativeImageJvm := "graalvm-java23",
    nativeImageOutput := {
      val outputDir = (target.value / "bin").toPath
      if (!Files.exists(outputDir)) {
        Files.createDirectories(outputDir)
      }
      outputDir.resolve("sbtn").toFile
    },
    nativeImageCommand := {
      val orig = nativeImageCommand.value
      sys.env.get("ARCHS") match {
        case Some(a) => Seq("arch", s"-$a") ++ orig
        case None    => orig
      }
    },
    nativeImageOptions ++= Seq(
      "--no-fallback",
      s"--initialize-at-run-time=sbt.client",
      // "The current machine does not support all of the following CPU features that are required by
      // the image: [CX8, CMOV, FXSR, MMX, SSE, SSE2, SSE3, SSSE3, SSE4_1, SSE4_2, POPCNT, LZCNT, AVX,
      // AVX2, BMI1, BMI2, FMA, F16C]."
      "-march=compatibility",
      // "--verbose",
      "-H:IncludeResourceBundles=jline.console.completer.CandidateListCompletionHandler",
      "-H:+ReportExceptionStackTraces",
      "-H:-ParseRuntimeOptions",
      s"-H:Name=${target.value / "bin" / "sbtn"}",
    ),
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
  .aggregate(lowerUtilProjects.map(p => LocalProject(p.id))*)
  .settings(
    Utils.noPublish
  )

lazy val upperModules = (project in (file("internal") / "upper"))
  .aggregate(
    ((allProjects diff lowerUtilProjects)
      diff Seq(bundledLauncherProj, lmCoursierShaded)).map(p => LocalProject(p.id))*
  )
  .settings(
    Utils.noPublish
  )

lazy val sbtIgnoredProblems = {
  import com.typesafe.tools.mima.core.*
  Vector(
  )
}

def scriptedTask(launch: Boolean): Def.Initialize[InputTask[Unit]] = Def.inputTask {
  val _ = publishLocalBinAll.value
  val launchJar = s"-Dsbt.launch.jar=${(bundledLauncherProj / Compile / packageBin).value}"
  Scripted.doScripted(
    (scriptedSbtProj / scalaInstance).value,
    scriptedSource.value,
    scriptedBufferLog.value,
    Def.setting(Scripted.scriptedParser(scriptedSource.value)).parsed,
    scriptedPrescripted.value,
    scriptedLaunchOpts.value ++ (if (launch) Some(launchJar) else None),
    scalaVersion.value,
    version.value,
    (scriptedSbtProj / Test / fullClasspathAsJars).value
      .map(_.data)
      .filterNot(_.getName.contains("scala-compiler")),
    (bundledLauncherProj / Compile / packageBin).value,
    streams.value.log,
    scriptedKeepTempDirectory.value
  )
}

lazy val publishLauncher = TaskKey[Unit]("publish-launcher")

lazy val sbtwProj = (project in file("sbtw"))
  .settings(
    commonSettings,
    name := "sbtw",
    description := "Windows drop-in launcher for sbt (replaces sbt.bat)",
    scalaVersion := "3.8.3",
    crossPaths := false,
    Compile / scalafix / unmanagedSources := {
      // https://github.com/scalameta/scalameta/issues/4531
      (Compile / unmanagedSources).value.filterNot(_.getName == "Main.scala")
    },
    Compile / mainClass := Some("sbtw.Main"),
    libraryDependencies += "com.github.scopt" %% "scopt" % "4.1.0",
    libraryDependencies += scalaVerify % Test,
    Utils.noPublish,
  )

def allProjects =
  Seq(
    logicProj,
    completeProj,
    testingProj,
    taskProj,
    stdTaskProj,
    runProj,
    scriptedSbtProj,
    protocolProj,
    actionsProj,
    commandProj,
    mainSettingsProj,
    zincLmIntegrationProj,
    mainProj,
    sbtIvyProj,
    sbtProj,
    bundledLauncherProj,
    sbtClientProj,
    sbtwProj,
    buildFileProj,
    utilCache,
    utilTracking,
    collectionProj,
    coreMacrosProj,
    remoteCacheProj,
    lmCore,
    lmIvy,
    lmCoursierDefinitions,
    lmCoursier,
    lmCoursierShaded,
    lmCoursierShadedPublishing,
    workerProj,
  ) ++ lowerUtilProjects

// These need to be cross published to 2.12 and 2.13 for Zinc
lazy val lowerUtilProjects =
  Seq(
    utilCore,
    utilControl,
    utilInterface,
    utilLogging,
    utilPosition,
    utilRelation,
    utilScripted,
  )

lazy val nonRoots = allProjects.map(p => LocalProject(p.id))

ThisBuild / scriptedBufferLog := true
ThisBuild / scriptedPrescripted := { _ => }
ThisBuild / scriptedKeepTempDirectory := false

def otherRootSettings =
  Seq(
    scripted := scriptedTask(true).evaluated,
    scriptedUnpublished := scriptedTask(true).evaluated,
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
        "-server"
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
    scriptedSbtProj,
    upperModules,
    lowerUtils,
  ),
  inConfigurations(Compile)
)
lazy val scriptedProjects = ScopeFilter(inAnyProject)

def customCommands: Seq[Setting[?]] = Seq(
  commands += Command.command("publishLocalAllModule") { state =>
    val extracted = Project.extract(state)
    import extracted.*
    val sv = get(scalaVersion)
    val projs = structure.allProjectRefs
    val ioOpt = projs find { case ProjectRef(_, id) => id == "ioRoot"; case _ => false }
    val utilOpt = projs find { case ProjectRef(_, id) => id == "utilRoot"; case _ => false }
    val lmOpt = projs find { case ProjectRef(_, id) => id == "lmRoot"; case _ => false }
    val zincOpt = projs find { case ProjectRef(_, id) => id == "zincRoot"; case _ => false }
    (ioOpt map { case ProjectRef(build, _) => "{" + build.toString + "}/publishLocal" }).toList :::
      (utilOpt map { case ProjectRef(build, _) =>
        "{" + build.toString + "}/publishLocal"
      }).toList :::
      (lmOpt map { case ProjectRef(build, _) =>
        "{" + build.toString + "}/publishLocal"
      }).toList :::
      (zincOpt map { case ProjectRef(build, _) =>
        val zincSv = get((ProjectRef(build, "zinc") / scalaVersion))
        val csv =
          getOpt((ProjectRef(build, "compilerBridge") / crossScalaVersions)).getOrElse(Nil).toList
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

ThisBuild / pomIncludeRepository := (_ => false) // drop repos other than Maven Central from POM
ThisBuild / publishTo := {
  val centralSnapshots = "https://central.sonatype.com/repository/maven-snapshots/"
  val v = (ThisBuild / version).value
  if (v.endsWith("SNAPSHOT")) Some("central-snapshots" at centralSnapshots)
  else localStaging.value
}
ThisBuild / publishMavenStyle := true

def lmTestSettings: Seq[Setting[?]] = Def.settings(
  Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat,
  Test / parallelExecution := false
)

lazy val lmCore = (project in file("lm-core"))
  .enablePlugins(ContrabandPlugin, JsonCodecPlugin)
  .settings(
    commonSettings,
    lmTestSettings,
    name := "librarymanagement-core",
    contrabandSjsonNewVersion := sjsonNewVersion,
    libraryDependencies ++= Seq(
      jsch,
      // scalaReflect,
      // scalaCompiler.value,
      launcherInterface,
      gigahorseApacheHttp,
      scalaXml,
      sjsonNewScalaJson.value % Optional,
      sjsonNewCore.value % Optional,
      scalacheck % Test,
      scalaVerify % Test,
      hedgehog % Test,
    ),
    libraryDependencies ++= scalatest,
    Compile / resourceGenerators += Def
      .task(
        Utils.generateVersionFile(
          version.value,
          resourceManaged.value,
          streams.value,
          (Compile / compile).value.asInstanceOf[Analysis]
        )
      )
      .taskValue,
    contrabandSettings,
    // WORKAROUND sbt/sbt#2205 include managed sources in packageSrc
    Compile / packageSrc / mappings ++= {
      val srcs = (Compile / managedSources).value
      val sdirs = (Compile / managedSourceDirectories).value
      val base = baseDirectory.value
      import Path.*
      (((srcs --- sdirs --- base) pair (relativeTo(sdirs) | relativeTo(base) | flat)) toSeq)
    },
    mimaSettings,
    mimaBinaryIssueFilters ++= Seq(
    ),
  )
  .dependsOn(utilLogging, utilPosition, utilCache)
  .configure(addSbtIO, addSbtCompilerInterface)

lazy val lmIvy = (project in file("lm-ivy"))
  .enablePlugins(ContrabandPlugin, JsonCodecPlugin)
  .dependsOn(lmCore)
  .settings(
    commonSettings,
    lmTestSettings,
    name := "librarymanagement-ivy",
    contrabandSjsonNewVersion := sjsonNewVersion,
    libraryDependencies ++= Seq(
      ivy,
      sjsonNewScalaJson.value,
      sjsonNewCore.value,
      scalacheck % Test,
      scalaVerify % Test,
      hedgehog % Test,
    ),
    libraryDependencies ++= scalatest,
    contrabandSettings,
    Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat,
    mimaSettings,
  )

lazy val lmCoursierSettings: Seq[Setting[?]] = Def.settings(
  baseSettings,
  headerLicense := Some(
    HeaderLicense.Custom(
      """|sbt
       |Copyright 2024, Scala Center
       |Copyright 2015 - 2023, Alexandre Archambault
       |Licensed under Apache License 2.0 (see LICENSE)
       |""".stripMargin
    )
  ),
  developers +=
    Developer(
      "alexarchambault",
      "Alexandre Archambault",
      "",
      url("https://github.com/alexarchambault")
    ),
)

lazy val lmCoursierDefinitions = project
  .in(file("lm-coursier/definitions"))
  .disablePlugins(MimaPlugin)
  .settings(
    lmCoursierSettings,
    scalafixDependencies += "net.hamnaberg" %% "dataclass-scalafix" % dataclassScalafixVersion,
    libraryDependencies ++= Seq(
      coursier,
      "net.hamnaberg" %% "dataclass-annotation" % dataclassScalafixVersion % Provided,
    ),
    conflictWarning := ConflictWarning.disable,
    Utils.noPublish,
  )
  .dependsOn(lmCore % "provided")

lazy val lmCoursierDependencies = Def.settings(
  libraryDependencies ++= Seq(
    coursier,
    coursierSbtMavenRepo,
    "io.get-coursier.jniutils" % "windows-jni-utils-lmcoursier" % jniUtilsVersion,
    "net.hamnaberg" %% "dataclass-annotation" % dataclassScalafixVersion % Provided,
  ),
  libraryDependencies ++= Dependencies.scalatest,
  excludeDependencies ++= Seq(
    ExclusionRule("org.scala-lang.modules", "scala-xml_2.13"),
  ),
)

lazy val lmCoursier = project
  .in(file("lm-coursier"))
  .enablePlugins(ContrabandPlugin)
  .settings(
    lmCoursierSettings,
    Mima.settings,
    Mima.lmCoursierFilters,
    lmCoursierDependencies,
    contrabandSettings,
    Compile / sourceGenerators += Utils.dataclassGen(lmCoursierDefinitions).taskValue,
  )
  .dependsOn(lmCore)

lazy val lmCoursierShaded = project
  .in(file("lm-coursier/target/shaded-module"))
  .settings(
    lmCoursierSettings,
    Mima.settings,
    Mima.lmCoursierFilters,
    Mima.lmCoursierShadedFilters,
    Compile / sources := (lmCoursier / Compile / sources).value,
    lmCoursierDependencies,
    autoScalaLibrary := false,
    bspEnabled := false,
    libraryDependencies ++= Seq(
      scala3Library % Provided,
    ),
    assembly / assemblyOption ~= { _.withIncludeScala(false) },
    conflictWarning := ConflictWarning.disable,
    Utils.noPublish,
    assemblyShadeRules := {
      val namespacesToShade = Seq(
        "coursier",
        "org.fusesource",
        "macrocompat",
        "io.github.alexarchambault.isterminal",
        "io.github.alexarchambault.windowsansi",
        "concurrentrefhashmap",
        "com.github.ghik",
        // pulled by the plexus-archiver stuff that coursier-cache
        // depends on for now… can hopefully be removed in the future
        "com.google.common",
        "com.jcraft",
        "com.lmax",
        "org.apache.commons",
        "org.apache.tika",
        "org.apache.xbean",
        "org.codehaus",
        "org.iq80",
        "org.tukaani",
        "com.github.plokhotnyuk.jsoniter_scala",
        "scala.cli",
        "com.github.luben.zstd",
        "javax.inject" // hope shading this is fine… It's probably pulled via plexus-archiver, that sbt shouldn't use anyway…
      )
      namespacesToShade.map { ns =>
        ShadeRule.rename(ns + ".**" -> s"lmcoursier.internal.shaded.$ns.@1").inAll
      }
    },
    assemblyMergeStrategy := {
      case PathList("lmcoursier", "internal", "shaded", "org", "fusesource", _*) =>
        MergeStrategy.first
      // case PathList("lmcoursier", "internal", "shaded", "package.class") => MergeStrategy.first
      // case PathList("lmcoursier", "internal", "shaded", "package$.class") => MergeStrategy.first
      case PathList("com", "github")          => MergeStrategy.discard
      case PathList("com", "jcraft")          => MergeStrategy.discard
      case PathList("com", "lmax")            => MergeStrategy.discard
      case PathList("com", "sun")             => MergeStrategy.discard
      case PathList("com", "swoval")          => MergeStrategy.discard
      case PathList("com", "typesafe")        => MergeStrategy.discard
      case PathList("gigahorse")              => MergeStrategy.discard
      case PathList("jline")                  => MergeStrategy.discard
      case PathList("scala", _*)              => MergeStrategy.discard
      case PathList("sjsonnew")               => MergeStrategy.discard
      case PathList("xsbti")                  => MergeStrategy.discard
      case PathList("META-INF", "native", _*) => MergeStrategy.first
      case "META-INF/services/lmcoursier.internal.shaded.coursier.jniutils.NativeApi" =>
        MergeStrategy.first
      case x =>
        val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
        oldStrategy(x)
    }
  )
  .dependsOn(lmCore % "provided")

lazy val lmCoursierShadedPublishing = project
  .in(file("lm-coursier/target/shaded-publishing-module"))
  .settings(
    scalaVersion := scala3,
    name := "librarymanagement-coursier",
    Compile / packageBin := (lmCoursierShaded / assembly).value,
    Compile / exportedProducts := Seq(Attributed.blank((Compile / packageBin).value))
  )

lazy val launcherPackage = (project in file("launcher-package"))
lazy val launcherPackageIntegrationTest =
  (project in (file("launcher-package") / "integration-test"))
    .dependsOn(sbtwProj)
    .settings(
      name := "integration-test",
      scalaVersion := scala3,
      libraryDependencies ++= Seq(
        scalaVerify % Test,
        hedgehog % Test,
        "com.lihaoyi" %% "ujson" % "3.1.0" % Test,
        // This needs to be hardcoded here, and not use addSbtIO
        "org.scala-sbt" %% "io" % "1.10.5" % Test,
      ),
      testFrameworks += TestFramework("hedgehog.sbt.Framework"),
      testFrameworks += TestFramework("verify.runner.Framework"),
      Test / fork := true,
      Test / javaOptions += {
        val cp = (Test / fullClasspath).value
          .map(_.data.getAbsolutePath)
          .mkString(java.io.File.pathSeparator)
        s"-Dsbt.test.classpath=$cp"
      },
      Test / javaOptions += s"-Dsbt.test.integrationtest.basedir=${(baseDirectory).value.getAbsolutePath}",
      Test / test := {
        (Test / test)
          .dependsOn(launcherPackage / Universal / packageBin)
          .dependsOn(launcherPackage / Universal / stage)
          .value
      },
      Test / testOnly := {
        (Test / testOnly)
          .dependsOn(launcherPackage / Universal / packageBin)
          .dependsOn(launcherPackage / Universal / stage)
          .evaluated
      },
      Test / parallelExecution := false
    )
