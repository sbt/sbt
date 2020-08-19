/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.util.concurrent.TimeUnit
import org.apache.logging.log4j.core.{ Appender => XAppender }
import org.scalasbt.ipcsocket.Win32SecurityLevel
import sbt.Def.{ ScopedKey, Setting }
import sbt.Keys._
import sbt.Project.{ inScope, inTask }
import sbt.Scope.GlobalScope
import sbt.SlashSyntax0._
import sbt.io._
import sbt.io.syntax._
import sbt.internal.librarymanagement._
import sbt.internal.nio.CheckBuildSources
import sbt.internal.server.BuildServerProtocol
import sbt.internal.util._
import sbt.internal.util.Types.idFun
import sbt.librarymanagement.CrossVersion.binarySbtVersion
import sbt.nio.Keys._
import sbt.nio.Watch
import sbt.nio.file.FileTreeView
import sbt.coursierint.LMCoursier
import sbt.librarymanagement.ivy.UpdateOptions
import scala.collection.immutable.ListMap
import scala.concurrent.duration._
import scala.xml.NodeSeq
import xsbti.compile.CompilerCache

import sbt.librarymanagement.Artifact.{ DocClassifier, SourceClassifier }
import sbt.librarymanagement._
import xsbti.CrossValue

private[sbt] object GlobalDefaults {
  private[sbt] def apply(ss: Seq[Def.Setting[_]]): Seq[Def.Setting[_]] =
    Def.defaultSettings(Project.inScope(Scope.GlobalScope)(ss))
  private[sbt] lazy val globalCore: Seq[Setting[_]] = GlobalDefaultsImpl.globalCore
  private[sbt] lazy val globalIvyCore: Seq[Setting[_]] = GlobalDefaultsImpl.globalIvyCore
  private[sbt] lazy val globalJvmCore: Seq[Setting[_]] = GlobalDefaultsImpl.globalJvmCore
  private[sbt] lazy val globalSbtCore: Seq[Setting[_]] = GlobalDefaultsImpl.globalSbtCore
  private[sbt] lazy val coreDefaultSettings = GlobalDefaultsImpl.coreDefaultSettings
  lazy val settings: Seq[Setting[_]] = GlobalDefaultsImpl.thisBuildCore ++ globalCore
}

private[internal] object GlobalDefaultsImpl {
  private[sbt] def thisBuildCore: Seq[Setting[_]] =
    inScope(GlobalScope.copy(project = Select(ThisBuild)))(
      Seq(
        managedDirectory := baseDirectory.value / "lib_managed"
      )
    )
  private[sbt] lazy val globalCore: Seq[Setting[_]] = GlobalDefaults(
    defaultTestTasks(test) ++ defaultTestTasks(testOnly) ++ defaultTestTasks(testQuick) ++ Seq(
      excludeFilter :== HiddenFileFilter,
      fileInputs :== Nil,
      fileInputIncludeFilter :== AllPassFilter.toNio,
      fileInputExcludeFilter :== DirectoryFilter.toNio || HiddenFileFilter,
      fileOutputIncludeFilter :== AllPassFilter.toNio,
      fileOutputExcludeFilter :== NothingFilter.toNio,
      inputFileStamper :== sbt.nio.FileStamper.Hash,
      outputFileStamper :== sbt.nio.FileStamper.LastModified,
      onChangedBuildSource :== sbt.nio.Keys.WarnOnSourceChanges,
      clean := { () },
      unmanagedFileStampCache :=
        state.value.get(persistentFileStampCache).getOrElse(new sbt.nio.FileStamp.Cache),
      managedFileStampCache := new sbt.nio.FileStamp.Cache,
    ) ++ globalIvyCore ++ globalJvmCore ++ Watch.defaults
  ) ++ globalSbtCore

  private[sbt] lazy val globalJvmCore: Seq[Setting[_]] =
    Seq(
      compilerCache := state.value get Keys.stateCompilerCache getOrElse CompilerCache.fresh,
      sourcesInBase :== true,
      autoAPIMappings := false,
      apiMappings := Map.empty,
      autoScalaLibrary :== true,
      managedScalaInstance :== true,
      classpathEntryDefinesClass := { (file: File) =>
        sys.error("use classpathEntryDefinesClassVF instead")
      },
      extraIncOptions :== Seq("JAVA_CLASS_VERSION" -> sys.props("java.class.version")),
      allowMachinePath :== true,
      traceLevel in run :== 0,
      traceLevel in runMain :== 0,
      traceLevel in bgRun :== 0,
      traceLevel in fgRun :== 0,
      traceLevel in console :== Int.MaxValue,
      traceLevel in consoleProject :== Int.MaxValue,
      autoCompilerPlugins :== true,
      scalaHome :== None,
      apiURL := None,
      javaHome :== None,
      discoveredJavaHomes := CrossJava.discoverJavaHomes,
      javaHomes :== ListMap.empty,
      fullJavaHomes := CrossJava.expandJavaHomes(discoveredJavaHomes.value ++ javaHomes.value),
      testForkedParallel :== false,
      javaOptions :== Nil,
      sbtPlugin :== false,
      isMetaBuild :== false,
      reresolveSbtArtifacts :== false,
      crossPaths :== true,
      sourcePositionMappers :== Nil,
      artifactClassifier in packageSrc :== Some(SourceClassifier),
      artifactClassifier in packageDoc :== Some(DocClassifier),
      includeFilter :== NothingFilter,
      includeFilter in unmanagedSources :== ("*.java": NameFilter) | "*.scala",
      includeFilter in unmanagedJars :== ("*.jar": NameFilter) | "*.so" | "*.dll" | "*.jnilib" | "*.zip",
      includeFilter in unmanagedResources :== AllPassFilter,
      bgList := { bgJobService.value.jobs },
      ps := BgJobsTasks.psTask.value,
      bgStop := BgJobsTasks.bgStopTask.evaluated,
      bgWaitFor := BgJobsTasks.bgWaitForTask.evaluated,
      bgCopyClasspath :== true,
      closeClassLoaders :== SysProp.closeClassLoaders,
      allowZombieClassLoaders :== true,
    ) ++ BuildServerProtocol.globalSettings

  private[sbt] lazy val globalIvyCore: Seq[Setting[_]] =
    Seq(
      internalConfigurationMap :== Configurations.internalMap _,
      credentials :== Nil,
      exportJars :== false,
      trackInternalDependencies :== TrackLevel.TrackAlways,
      exportToInternal :== TrackLevel.TrackAlways,
      useCoursier :== SysProp.defaultUseCoursier,
      retrieveManaged :== false,
      retrieveManagedSync :== false,
      configurationsToRetrieve :== None,
      scalaOrganization :== ScalaArtifacts.Organization,
      scalaArtifacts :== ScalaArtifacts.Artifacts,
      sbtResolver := {
        val v = sbtVersion.value
        if (v.endsWith("-SNAPSHOT") || v.contains("-bin-")) Classpaths.sbtMavenSnapshots
        else Resolver.DefaultMavenRepository
      },
      sbtResolvers := {
        // TODO: Remove Classpaths.typesafeReleases for sbt 2.x
        // We need to keep it around for sbt 1.x to cross build plugins with sbt 0.13 - https://github.com/sbt/sbt/issues/4698
        Vector(sbtResolver.value, Classpaths.sbtPluginReleases, Classpaths.typesafeReleases)
      },
      crossVersion :== Disabled(),
      buildDependencies := Classpaths.constructBuildDependencies.value,
      version :== "0.1.0-SNAPSHOT",
      versionScheme :== None,
      classpathTypes :== Set("jar", "bundle", "maven-plugin", "test-jar") ++ CustomPomParser.JarPackagings,
      artifactClassifier :== None,
      checksums := Classpaths.bootChecksums(appConfiguration.value),
      conflictManager := ConflictManager.default,
      CustomHttp.okhttpClientBuilder :== CustomHttp.defaultHttpClientBuilder,
      CustomHttp.okhttpClient := CustomHttp.okhttpClientBuilder.value.build,
      pomExtra :== NodeSeq.Empty,
      pomPostProcess :== idFun,
      pomAllRepositories :== false,
      pomIncludeRepository :== Classpaths.defaultRepositoryFilter,
      updateOptions := UpdateOptions(),
      forceUpdatePeriod :== None,
      // coursier settings
      csrExtraCredentials :== Nil,
      csrLogger := LMCoursier.coursierLoggerTask.value,
      csrMavenProfiles :== Set.empty,
      csrReconciliations :== LMCoursier.relaxedForAllModules,
    )

  /** Core non-plugin settings for sbt builds.  These *must* be on every build or the sbt engine will fail to run at all. */
  private[sbt] lazy val globalSbtCore: Seq[Setting[_]] = GlobalDefaults(
    Seq(
      outputStrategy :== None, // TODO - This might belong elsewhere.
      buildStructure := Project.structure(state.value),
      settingsData := buildStructure.value.data,
      aggregate in checkBuildSources :== false,
      aggregate in checkBuildSources / changedInputFiles := false,
      checkBuildSources / Continuous.dynamicInputs := None,
      checkBuildSources / fileInputs := CheckBuildSources.buildSourceFileInputs.value,
      checkBuildSources := CheckBuildSources.needReloadImpl.value,
      fileCacheSize := "128M",
      trapExit :== true,
      connectInput :== false,
      cancelable :== true,
      taskCancelStrategy := { state: State =>
        if (cancelable.value) TaskCancellationStrategy.Signal
        else TaskCancellationStrategy.Null
      },
      envVars :== Map.empty,
      sbtVersion := appConfiguration.value.provider.id.version,
      sbtBinaryVersion := binarySbtVersion(sbtVersion.value),
      // `pluginCrossBuild` scoping is based on sbt-cross-building plugin.
      // The idea here is to be able to define a `sbtVersion in pluginCrossBuild`, which
      // directs the dependencies of the plugin to build to the specified sbt plugin version.
      sbtVersion in pluginCrossBuild := sbtVersion.value,
      onLoad := idFun[State],
      onUnload := idFun[State],
      onUnload := { s =>
        try onUnload.value(s)
        finally IO.delete(taskTemporaryDirectory.value)
      },
      // extraLoggers is deprecated
      SettingKey[ScopedKey[_] => Seq[XAppender]]("extraLoggers") :== { _ =>
        Nil
      },
      extraAppenders := {
        val f = SettingKey[ScopedKey[_] => Seq[XAppender]]("extraLoggers").value
        s =>
          f(s).map {
            case a: Appender => a
            case a           => new ConsoleAppenderFromLog4J(a.getName, a)
          }
      },
      useLog4J :== SysProp.useLog4J,
      watchSources :== Nil, // Although this is deprecated, it can't be removed or it breaks += for legacy builds.
      skip :== false,
      taskTemporaryDirectory := {
        val base = BuildPaths.globalTaskDirectoryStandard(appConfiguration.value.baseDirectory)
        val dir = IO.createUniqueDirectory(base)
        ShutdownHooks.add(() => IO.delete(dir))
        dir
      },
      onComplete := {
        val tempDirectory = taskTemporaryDirectory.value
        () => Clean.deleteContents(tempDirectory, _ => false)
      },
      turbo :== SysProp.turbo,
      usePipelining :== SysProp.pipelining,
      exportPipelining := usePipelining.value,
      useScalaReplJLine :== false,
      scalaInstanceTopLoader := {
        if (!useScalaReplJLine.value) classOf[org.jline.terminal.Terminal].getClassLoader
        else appConfiguration.value.provider.scalaProvider.launcher.topLoader.getParent
      },
      useSuperShell := { if (insideCI.value) false else Terminal.console.isSupershellEnabled },
      superShellThreshold :== SysProp.supershellThreshold,
      superShellMaxTasks :== SysProp.supershellMaxTasks,
      superShellSleep :== SysProp.supershellSleep.millis,
      progressReports := {
        val rs = EvaluateTask.taskTimingProgress.toVector ++ EvaluateTask.taskTraceEvent.toVector
        rs map { Keys.TaskProgress(_) }
      },
      // progressState is deprecated
      SettingKey[Option[ProgressState]]("progressState") := None,
      Previous.cache := new Previous(
        Def.streamsManagerKey.value,
        Previous.references.value.getReferences
      ),
      Previous.references :== new Previous.References,
      concurrentRestrictions := defaultRestrictions.value,
      parallelExecution :== true,
      fileTreeView :== FileTreeView.default,
      Continuous.dynamicInputs := Continuous.dynamicInputsImpl.value,
      logBuffered :== false,
      commands :== Nil,
      showSuccess :== true,
      showTiming :== true,
      timingFormat :== Aggregation.defaultFormat,
      aggregate :== true,
      maxErrors :== 100,
      fork :== false,
      initialize :== {},
      templateResolverInfos :== Nil,
      forcegc :== sys.props
        .get("sbt.task.forcegc")
        .map(java.lang.Boolean.parseBoolean)
        .getOrElse(GCUtil.defaultForceGarbageCollection),
      minForcegcInterval :== GCUtil.defaultMinForcegcInterval,
      interactionService :== CommandLineUIService,
      autoStartServer := true,
      serverHost := "127.0.0.1",
      serverIdleTimeout := Some(new FiniteDuration(7, TimeUnit.DAYS)),
      serverPort := 5000 + (Hash
        .toHex(Hash(appConfiguration.value.baseDirectory.toString))
        .## % 1000),
      serverConnectionType := ConnectionType.Local,
      serverAuthentication := {
        if (serverConnectionType.value == ConnectionType.Tcp) Set(ServerAuthentication.Token)
        else Set()
      },
      serverHandlers :== Nil,
      windowsServerSecurityLevel := Win32SecurityLevel.OWNER_DACL, // allows any owner logon session to access the server
      fullServerHandlers := Nil,
      insideCI :== sys.env.contains("BUILD_NUMBER") ||
        sys.env.contains("CI") || SysProp.ci,
      // watch related settings
      pollInterval :== Watch.defaultPollInterval,
    ) ++ LintUnused.lintSettings
      ++ DefaultBackgroundJobService.backgroundJobServiceSettings
      ++ RemoteCache.globalSettings
  )

  // These are project level settings that MUST be on every project.
  private[sbt] lazy val coreDefaultSettings: Seq[Setting[_]] =
    projectCore ++ disableAggregation ++ Seq(
      // Missing but core settings
      baseDirectory := thisProject.value.base,
      target := baseDirectory.value / "target",
      // Use (sbtVersion in pluginCrossBuild) to pick the sbt module to depend from the plugin.
      // Because `sbtVersion in pluginCrossBuild` can be scoped to project level,
      // this setting needs to be set here too.
      sbtDependency in pluginCrossBuild := {
        val app = appConfiguration.value
        val id = app.provider.id
        val sv = (sbtVersion in pluginCrossBuild).value
        val scalaV = (scalaVersion in pluginCrossBuild).value
        val binVersion = (scalaBinaryVersion in pluginCrossBuild).value
        val cross = id.crossVersionedValue match {
          case CrossValue.Disabled => Disabled()
          case CrossValue.Full     => CrossVersion.full
          case CrossValue.Binary   => CrossVersion.binary
        }
        val base = ModuleID(id.groupID, id.name, sv).withCrossVersion(cross)
        CrossVersion(scalaV, binVersion)(base).withCrossVersion(Disabled())
      },
      bgHashClasspath := !turbo.value,
      classLoaderLayeringStrategy := {
        if (turbo.value) ClassLoaderLayeringStrategy.AllLibraryJars
        else ClassLoaderLayeringStrategy.ScalaLibrary
      },
    )

  private[sbt] val noAggregation: Seq[Scoped] =
    Seq(run, runMain, bgRun, bgRunMain, console, consoleQuick, consoleProject)
  private[sbt] lazy val disableAggregation = GlobalDefaults(noAggregation map disableAggregate)
  private def disableAggregate(k: Scoped) = aggregate in k :== false
  private[sbt] val projectCore: Seq[Setting[_]] = Seq(
    name := thisProject.value.id,
    logManager := LogManager.defaults(extraAppenders.value, ConsoleOut.terminalOut),
    onLoadMessage := (onLoadMessage or
      Def.setting {
        s"set current project to ${name.value} (in build ${thisProjectRef.value.build})"
      }).value
  )

  private[sbt] def defaultRestrictions: Def.Initialize[Seq[Tags.Rule]] =
    Def.setting {
      val par = parallelExecution.value
      val max = EvaluateTask.SystemProcessors
      Tags.limitAll(if (par) max else 1) ::
        Tags.limit(Tags.ForkedTestGroup, 1) ::
        Tags.exclusiveGroup(Tags.Clean) ::
        Nil
    }

  private def defaultTestTasks(key: Scoped): Seq[Setting[_]] =
    inTask(key)(Seq(tags := Seq(Tags.Test -> 1), logBuffered := true))
}
