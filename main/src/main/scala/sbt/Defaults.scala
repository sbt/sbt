/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.io.{ File, PrintWriter }
import java.net.{ URI, URL }
import java.nio.file.{ Paths, Path => NioPath }
import java.util.Optional
import java.util.concurrent.TimeUnit

import lmcoursier.CoursierDependencyResolution
import org.apache.logging.log4j.core.{ Appender => XAppender }
import org.scalasbt.ipcsocket.Win32SecurityLevel
import sbt.Def.{ Initialize, ScopedKey, Setting, SettingsDefinition }
import sbt.Keys._
import sbt.Project.{
  inConfig,
  inScope,
  inTask,
  richInitializeTask,
  richTaskSessionVar,
  sbtRichTaskPromise,
}
import sbt.Scope.{ GlobalScope, ThisScope, fillTaskAxis }
import sbt.coursierint._
import sbt.internal.CommandStrings.ExportStream
import sbt.internal._
import sbt.internal.classpath.{ AlternativeZincUtil, ClassLoaderCache }
import sbt.internal.inc.JavaInterfaceUtil._
import sbt.internal.inc.classpath.{ ClasspathFilter, ClasspathUtil }
import sbt.internal.inc.{ CompileOutput, MappedFileConverter, Stamps, ZincLmUtil, ZincUtil }
import sbt.internal.io.{ Source, WatchState }
import sbt.internal.librarymanagement.mavenint.PomExtraDependencyAttributes
import sbt.internal.librarymanagement.{ CustomHttp => _, _ }
import sbt.internal.nio.{ CheckBuildSources, Globs }
import sbt.internal.server.{
  BspCompileTask,
  BuildServerProtocol,
  BuildServerReporter,
  Definition,
  LanguageServerProtocol,
  ServerHandler,
  VirtualTerminal,
}
import sbt.internal.testing.TestLogger
import sbt.internal.util.Attributed.data
import sbt.internal.util.Types._
import sbt.internal.util._
import sbt.internal.util.complete._
import sbt.io.Path._
import sbt.io._
import sbt.io.syntax._
import sbt.librarymanagement.Artifact.{ DocClassifier, SourceClassifier }
import sbt.librarymanagement.Configurations.{ Compile, IntegrationTest, Runtime, Test }
import sbt.librarymanagement.CrossVersion.{ binarySbtVersion, binaryScalaVersion }
import sbt.librarymanagement._
import sbt.librarymanagement.ivy._
import sbt.librarymanagement.syntax._
import sbt.nio.Keys._
import sbt.nio.file.syntax._
import sbt.nio.file.FileTreeView
import sbt.nio.Watch
import sbt.std.TaskExtra._
import sbt.testing.{ AnnotatedFingerprint, Framework, Runner, SubclassFingerprint }
import sbt.util.CacheImplicits._
import sbt.util.InterfaceUtil.{ toJavaFunction => f1, t2 }
import sbt.util._
import sjsonnew._

import scala.collection.immutable.ListMap
import scala.concurrent.duration._
import scala.xml.NodeSeq

// incremental compiler
import sbt.SlashSyntax0._
import sbt.internal.inc.{
  Analysis,
  AnalyzingCompiler,
  ManagedLoggedReporter,
  MixedAnalyzingCompiler,
  ScalaInstance
}
import xsbti.{ CrossValue, VirtualFile, VirtualFileRef }
import xsbti.compile.{
  AnalysisContents,
  ClassFileManagerType,
  ClasspathOptionsUtil,
  CompileAnalysis,
  CompileOptions,
  CompileOrder,
  CompileResult,
  CompileProgress,
  CompilerCache,
  Compilers,
  DefinesClass,
  IncOptions,
  IncToolOptionsUtil,
  Inputs,
  MiniSetup,
  PerClasspathEntryLookup,
  PreviousResult,
  Setup,
  TransactionalManagerType
}

object Defaults extends BuildCommon {
  final val CacheDirectoryName = "cache"

  def configSrcSub(key: SettingKey[File]): Initialize[File] =
    Def.setting {
      (key in ThisScope.copy(config = Zero)).value / nameForSrc(configuration.value.name)
    }
  def nameForSrc(config: String) = if (config == Configurations.Compile.name) "main" else config
  def prefix(config: String) = if (config == Configurations.Compile.name) "" else config + "-"

  def lock(app: xsbti.AppConfiguration): xsbti.GlobalLock = LibraryManagement.lock(app)

  def extractAnalysis[T](a: Attributed[T]): (T, CompileAnalysis) =
    (a.data, a.metadata get Keys.analysis getOrElse Analysis.Empty)

  def analysisMap[T](cp: Seq[Attributed[T]]): T => Option[CompileAnalysis] = {
    val m = (for (a <- cp; an <- a.metadata get Keys.analysis) yield (a.data, an)).toMap
    m.get _
  }
  private[sbt] def globalDefaults(ss: Seq[Setting[_]]): Seq[Setting[_]] =
    Def.defaultSettings(inScope(GlobalScope)(ss))

  def buildCore: Seq[Setting[_]] = thisBuildCore ++ globalCore
  def thisBuildCore: Seq[Setting[_]] =
    inScope(GlobalScope.copy(project = Select(ThisBuild)))(
      Seq(
        managedDirectory := baseDirectory.value / "lib_managed"
      )
    )
  private[sbt] lazy val globalCore: Seq[Setting[_]] = globalDefaults(
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
      includeFilter in unmanagedSources :== ("*.java" | "*.scala"),
      includeFilter in unmanagedJars :== "*.jar" | "*.so" | "*.dll" | "*.jnilib" | "*.zip",
      includeFilter in unmanagedResources :== AllPassFilter,
      bgList := { bgJobService.value.jobs },
      ps := psTask.value,
      bgStop := bgStopTask.evaluated,
      bgWaitFor := bgWaitForTask.evaluated,
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
  private[sbt] lazy val globalSbtCore: Seq[Setting[_]] = globalDefaults(
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

  private[sbt] lazy val buildLevelJvmSettings: Seq[Setting[_]] = Seq(
    exportPipelining := usePipelining.value,
    rootPaths := {
      val app = appConfiguration.value
      val base = app.baseDirectory.getCanonicalFile
      val boot = app.provider.scalaProvider.launcher.bootDirectory
      val ih = app.provider.scalaProvider.launcher.ivyHome
      val coursierCache = csrCacheDirectory.value
      val javaHome = Paths.get(sys.props("java.home"))
      Map(
        "BASE" -> base.toPath,
        "SBT_BOOT" -> boot.toPath,
        "CSR_CACHE" -> coursierCache.toPath,
        "IVY_HOME" -> ih.toPath,
        "JAVA_HOME" -> javaHome,
      )
    },
    fileConverter := MappedFileConverter(rootPaths.value, allowMachinePath.value),
    // The virtual file value cache needs to be global or sbt will run out of direct byte buffer memory.
    classpathDefinesClassCache := VirtualFileValueCache.definesClassCache(fileConverter.value),
    fullServerHandlers := {
      Seq(
        LanguageServerProtocol.handler(fileConverter.value),
        BuildServerProtocol
          .handler(sbtVersion.value, semanticdbEnabled.value, semanticdbVersion.value),
        VirtualTerminal.handler,
      ) ++ serverHandlers.value :+ ServerHandler.fallback
    },
    timeWrappedStamper := Stamps
      .timeWrapBinaryStamps(Stamps.uncachedStamps(fileConverter.value), fileConverter.value),
    reusableStamper := {
      val converter = fileConverter.value
      val unmanagedCache = unmanagedFileStampCache.value
      val managedCache = managedFileStampCache.value
      val backing = timeWrappedStamper.value
      new xsbti.compile.analysis.ReadStamps {
        def getAllLibraryStamps()
            : java.util.Map[xsbti.VirtualFileRef, xsbti.compile.analysis.Stamp] =
          backing.getAllLibraryStamps()
        def getAllProductStamps()
            : java.util.Map[xsbti.VirtualFileRef, xsbti.compile.analysis.Stamp] =
          backing.getAllProductStamps()
        def getAllSourceStamps()
            : java.util.Map[xsbti.VirtualFileRef, xsbti.compile.analysis.Stamp] =
          new java.util.HashMap[xsbti.VirtualFileRef, xsbti.compile.analysis.Stamp]
        def library(fr: xsbti.VirtualFileRef): xsbti.compile.analysis.Stamp = backing.library(fr)
        def product(fr: xsbti.VirtualFileRef): xsbti.compile.analysis.Stamp = backing.product(fr)
        def source(fr: xsbti.VirtualFile): xsbti.compile.analysis.Stamp = {
          val path = converter.toPath(fr)
          unmanagedCache
            .get(path)
            .orElse(managedCache.getOrElseUpdate(path, sbt.nio.FileStamper.Hash))
            .map(_.stamp)
            .getOrElse(backing.source(fr))
        }
      }
    },
  )

  // csrCacheDirectory is scoped to ThisBuild to allow customization.
  private[sbt] lazy val buildLevelIvySettings: Seq[Setting[_]] = Seq(
    csrCacheDirectory := {
      if (useCoursier.value) LMCoursier.defaultCacheLocation
      else Classpaths.dummyCoursierDirectory(appConfiguration.value)
    },
  )

  def defaultTestTasks(key: Scoped): Seq[Setting[_]] =
    inTask(key)(
      Seq(
        tags := Seq(Tags.Test -> 1),
        logBuffered := true
      )
    )

  // TODO: This should be on the new default settings for a project.
  def projectCore: Seq[Setting[_]] = Seq(
    name := thisProject.value.id,
    logManager := LogManager.defaults(extraAppenders.value, ConsoleOut.terminalOut),
    onLoadMessage := (onLoadMessage or
      Def.setting {
        s"set current project to ${name.value} (in build ${thisProjectRef.value.build})"
      }).value
  )

  // Appended to JvmPlugin.projectSettings
  def paths: Seq[Setting[_]] = Seq(
    baseDirectory := thisProject.value.base,
    target := baseDirectory.value / "target",
    // Use a different history path for jline3 because the jline2 format is
    // incompatible. By sbt 1.4.0, we should consider revering this to t / ".history"
    // and possibly rewriting the jline2 history in a jline3 compatible format if the
    // history file is incompatible. For now, just use a different file to facilitate
    // going back and forth between 1.3.x and 1.4.x.
    historyPath := (historyPath or target(t => Option(t / ".history3"))).value,
    sourceDirectory := baseDirectory.value / "src",
    sourceManaged := crossTarget.value / "src_managed",
    resourceManaged := crossTarget.value / "resource_managed",
    // Adds subproject build.sbt files to the global list of build files to monitor
    Scope.Global / checkBuildSources / pollInterval :==
      new FiniteDuration(Int.MinValue, TimeUnit.MILLISECONDS),
    Scope.Global / checkBuildSources / fileInputs += baseDirectory.value.toGlob / "*.sbt",
  )

  lazy val configPaths = sourceConfigPaths ++ resourceConfigPaths ++ outputConfigPaths
  lazy val sourceConfigPaths = Seq(
    sourceDirectory := configSrcSub(sourceDirectory).value,
    sourceManaged := configSrcSub(sourceManaged).value,
    scalaSource := sourceDirectory.value / "scala",
    javaSource := sourceDirectory.value / "java",
    unmanagedSourceDirectories := {
      makeCrossSources(
        scalaSource.value,
        javaSource.value,
        scalaBinaryVersion.value,
        crossPaths.value
      ) ++
        makePluginCrossSources(
          sbtPlugin.value,
          scalaSource.value,
          (sbtBinaryVersion in pluginCrossBuild).value,
          crossPaths.value
        )
    },
    unmanagedSources / fileInputs := {
      val include = (includeFilter in unmanagedSources).value
      val filter = (excludeFilter in unmanagedSources).value match {
        // Hidden files are already filtered out by the FileStamps method
        case NothingFilter | HiddenFileFilter => include
        case exclude                          => include -- exclude
      }
      val baseSources =
        if (sourcesInBase.value) Globs(baseDirectory.value.toPath, recursive = false, filter) :: Nil
        else Nil
      unmanagedSourceDirectories.value
        .map(d => Globs(d.toPath, recursive = true, filter)) ++ baseSources
    },
    unmanagedSources := (unmanagedSources / inputFileStamps).value.map(_._1.toFile),
    managedSourceDirectories := Seq(sourceManaged.value),
    managedSources := {
      val stamper = inputFileStamper.value
      val cache = managedFileStampCache.value
      val res = generate(sourceGenerators).value
      res.foreach { f =>
        cache.putIfAbsent(f.toPath, stamper)
      }
      res
    },
    managedSourcePaths / outputFileStamper := sbt.nio.FileStamper.Hash,
    managedSourcePaths := managedSources.value.map(_.toPath),
    sourceGenerators :== Nil,
    sourceDirectories := Classpaths
      .concatSettings(unmanagedSourceDirectories, managedSourceDirectories)
      .value,
    sources := Classpaths.concatDistinct(unmanagedSources, managedSources).value
  )
  lazy val resourceConfigPaths = Seq(
    resourceDirectory := sourceDirectory.value / "resources",
    resourceManaged := configSrcSub(resourceManaged).value,
    unmanagedResourceDirectories := Seq(resourceDirectory.value),
    managedResourceDirectories := Seq(resourceManaged.value),
    resourceDirectories := Classpaths
      .concatSettings(unmanagedResourceDirectories, managedResourceDirectories)
      .value,
    unmanagedResources / fileInputs := {
      val include = (includeFilter in unmanagedResources).value
      val filter = (excludeFilter in unmanagedResources).value match {
        // Hidden files are already filtered out by the FileStamps method
        case NothingFilter | HiddenFileFilter => include
        case exclude                          => include -- exclude
      }
      unmanagedResourceDirectories.value.map(d => Globs(d.toPath, recursive = true, filter))
    },
    unmanagedResources := (unmanagedResources / inputFileStamps).value.map(_._1.toFile),
    resourceGenerators :== Nil,
    resourceGenerators += Def.task {
      PluginDiscovery.writeDescriptors(discoveredSbtPlugins.value, resourceManaged.value)
    },
    managedResources := generate(resourceGenerators).value,
    resources := Classpaths.concat(managedResources, unmanagedResources).value
  )
  // This exists for binary compatibility and probably never should have been public.
  def addBaseSources: Seq[Def.Setting[Task[Seq[File]]]] = Nil
  lazy val outputConfigPaths: Seq[Setting[_]] = Seq(
    classDirectory := crossTarget.value / (prefix(configuration.value.name) + "classes"),
    backendOutput := {
      val converter = fileConverter.value
      val dir = classDirectory.value
      converter.toVirtualFile(dir.toPath)
    },
    earlyOutput / artifactPath := earlyArtifactPathSetting(artifact).value,
    earlyOutput := {
      val converter = fileConverter.value
      val jar = (earlyOutput / artifactPath).value
      converter.toVirtualFile(jar.toPath)
    },
    semanticdbTargetRoot := crossTarget.value / (prefix(configuration.value.name) + "meta"),
    compileAnalysisTargetRoot := crossTarget.value / (prefix(configuration.value.name) + "zinc"),
    earlyCompileAnalysisTargetRoot := crossTarget.value / (prefix(configuration.value.name) + "early-zinc"),
    target in doc := crossTarget.value / (prefix(configuration.value.name) + "api")
  )

  // This is included into JvmPlugin.projectSettings
  def compileBase = inTask(console)(compilersSetting :: Nil) ++ compileBaseGlobal ++ Seq(
    scalaInstance := scalaInstanceTask.value,
    crossVersion := (if (crossPaths.value) CrossVersion.binary else CrossVersion.disabled),
    sbtBinaryVersion in pluginCrossBuild := binarySbtVersion(
      (sbtVersion in pluginCrossBuild).value
    ),
    crossSbtVersions := Vector((sbtVersion in pluginCrossBuild).value),
    crossTarget := makeCrossTarget(
      target.value,
      scalaBinaryVersion.value,
      (sbtBinaryVersion in pluginCrossBuild).value,
      sbtPlugin.value,
      crossPaths.value
    ),
    cleanIvy := IvyActions.cleanCachedResolutionCache(ivyModule.value, streams.value.log),
    clean := clean.dependsOn(cleanIvy).value,
    scalaCompilerBridgeBinaryJar := None,
    scalaCompilerBridgeSource := ZincLmUtil.getDefaultBridgeModule(scalaVersion.value),
    consoleProject / scalaCompilerBridgeSource := ZincLmUtil.getDefaultBridgeModule(
      appConfiguration.value.provider.scalaProvider.version
    ),
  )
  // must be a val: duplication detected by object identity
  private[this] lazy val compileBaseGlobal: Seq[Setting[_]] = globalDefaults(
    Seq(
      incOptions := IncOptions.of(),
      classpathOptions :== ClasspathOptionsUtil.boot,
      classpathOptions in console :== ClasspathOptionsUtil.repl,
      compileOrder :== CompileOrder.Mixed,
      javacOptions :== Nil,
      scalacOptions :== Nil,
      scalaVersion := appConfiguration.value.provider.scalaProvider.version,
      derive(crossScalaVersions := Seq(scalaVersion.value)),
      derive(compilersSetting),
      derive(scalaBinaryVersion := binaryScalaVersion(scalaVersion.value))
    )
  )

  def makeCrossSources(
      scalaSrcDir: File,
      javaSrcDir: File,
      sv: String,
      cross: Boolean
  ): Seq[File] = {
    if (cross)
      Seq(scalaSrcDir.getParentFile / s"${scalaSrcDir.name}-$sv", scalaSrcDir, javaSrcDir)
    else
      Seq(scalaSrcDir, javaSrcDir)
  }

  def makePluginCrossSources(
      isPlugin: Boolean,
      scalaSrcDir: File,
      sbtBinaryV: String,
      cross: Boolean
  ): Seq[File] = {
    if (cross && isPlugin)
      Vector(scalaSrcDir.getParentFile / s"${scalaSrcDir.name}-sbt-$sbtBinaryV")
    else Vector()
  }

  def makeCrossTarget(t: File, sv: String, sbtv: String, plugin: Boolean, cross: Boolean): File = {
    val scalaBase = if (cross) t / ("scala-" + sv) else t
    if (plugin) scalaBase / ("sbt-" + sbtv) else scalaBase
  }

  def compilersSetting = {
    compilers := {
      val st = state.value
      val g = BuildPaths.getGlobalBase(st)
      val zincDir = BuildPaths.getZincDirectory(st, g)
      val app = appConfiguration.value
      val launcher = app.provider.scalaProvider.launcher
      val dr = scalaCompilerBridgeDependencyResolution.value
      val scalac =
        scalaCompilerBridgeBinaryJar.value match {
          case Some(jar) =>
            AlternativeZincUtil.scalaCompiler(
              scalaInstance = scalaInstance.value,
              classpathOptions = classpathOptions.value,
              compilerBridgeJar = jar,
              classLoaderCache = st.get(BasicKeys.classLoaderCache)
            )
          case _ =>
            ZincLmUtil.scalaCompiler(
              scalaInstance = scalaInstance.value,
              classpathOptions = classpathOptions.value,
              globalLock = launcher.globalLock,
              componentProvider = app.provider.components,
              secondaryCacheDir = Option(zincDir),
              dependencyResolution = dr,
              compilerBridgeSource = scalaCompilerBridgeSource.value,
              scalaJarsTarget = zincDir,
              classLoaderCache = st.get(BasicKeys.classLoaderCache),
              log = streams.value.log
            )
        }
      val compilers = ZincUtil.compilers(
        instance = scalaInstance.value,
        classpathOptions = classpathOptions.value,
        javaHome = javaHome.value.map(_.toPath),
        scalac
      )
      val classLoaderCache = state.value.classLoaderCache
      if (java.lang.Boolean.getBoolean("sbt.disable.interface.classloader.cache")) compilers
      else {
        compilers.withScalac(
          compilers.scalac match {
            case x: AnalyzingCompiler => x.withClassLoaderCache(classLoaderCache)
            case x                    => x
          }
        )
      }
    }
  }

  def defaultCompileSettings: Seq[Setting[_]] =
    globalDefaults(enableBinaryCompileAnalysis := true)

  lazy val configTasks: Seq[Setting[_]] = docTaskSettings(doc) ++
    inTask(compile)(compileInputsSettings) ++
    inTask(compileJava)(compileInputsSettings(dependencyVirtualClasspath)) ++
    configGlobal ++ defaultCompileSettings ++ compileAnalysisSettings ++ Seq(
    compileOutputs := {
      import scala.collection.JavaConverters._
      val c = fileConverter.value
      val classFiles =
        manipulateBytecode.value.analysis.readStamps.getAllProductStamps.keySet.asScala
      (classFiles.toSeq map { x =>
        c.toPath(x)
      }) :+ compileAnalysisFile.value.toPath
    },
    compileOutputs := compileOutputs.triggeredBy(compile).value,
    clean := (compileOutputs / clean).value,
    earlyOutputPing := Def.promise[Boolean],
    compileProgress := {
      val s = streams.value
      val promise = earlyOutputPing.value
      val mn = moduleName.value
      val c = configuration.value
      new CompileProgress {
        override def afterEarlyOutput(isSuccess: Boolean): Unit = {
          if (isSuccess) s.log.debug(s"[$mn / $c] early output is success")
          else s.log.debug(s"[$mn / $c] early output can't be made because of macros")
          promise.complete(Value(isSuccess))
        }
      }
    },
    compileEarly := compileEarlyTask.value,
    compile := compileTask.value,
    compileScalaBackend := compileScalaBackendTask.value,
    compileJava := compileJavaTask.value,
    compileSplit := {
      // conditional task
      if (incOptions.value.pipelining) compileJava.value
      else compileScalaBackend.value
    },
    internalDependencyConfigurations := InternalDependencies.configurations.value,
    manipulateBytecode := compileSplit.value,
    compileIncremental := compileIncrementalTask.tag(Tags.Compile, Tags.CPU).value,
    printWarnings := printWarningsTask.value,
    compileAnalysisFilename := {
      // Here, if the user wants cross-scala-versioning, we also append it
      // to the analysis cache, so we keep the scala versions separated.
      val binVersion = scalaBinaryVersion.value
      val extra =
        if (crossPaths.value) s"_$binVersion"
        else ""
      s"inc_compile$extra.zip"
    },
    earlyCompileAnalysisFile := {
      earlyCompileAnalysisTargetRoot.value / compileAnalysisFilename.value
    },
    compileAnalysisFile := {
      compileAnalysisTargetRoot.value / compileAnalysisFilename.value
    },
    externalHooks := IncOptions.defaultExternal,
    incOptions := {
      val old = incOptions.value
      old
        .withExternalHooks(externalHooks.value)
        .withClassfileManagerType(
          Option(
            TransactionalManagerType
              .of( // https://github.com/sbt/sbt/issues/1673
                crossTarget.value / s"${prefix(configuration.value.name)}classes.bak",
                sbt.util.Logger.Null
              ): ClassFileManagerType
          ).toOptional
        )
        .withPipelining(usePipelining.value)
    },
    scalacOptions := {
      val old = scalacOptions.value
      val converter = fileConverter.value
      if (exportPipelining.value)
        Vector("-Ypickle-java", "-Ypickle-write", converter.toPath(earlyOutput.value).toString) ++ old
      else old
    },
    persistJarClasspath :== true,
    classpathEntryDefinesClassVF := {
      (if (persistJarClasspath.value) classpathDefinesClassCache.value
       else VirtualFileValueCache.definesClassCache(fileConverter.value)).get
    },
    compileIncSetup := compileIncSetupTask.value,
    console := consoleTask.value,
    collectAnalyses := Definition.collectAnalysesTask.map(_ => ()).value,
    consoleQuick := consoleQuickTask.value,
    discoveredMainClasses := (compile map discoverMainClasses storeAs discoveredMainClasses xtriggeredBy compile).value,
    discoveredSbtPlugins := discoverSbtPluginNames.value,
    // This fork options, scoped to the configuration is used for tests
    forkOptions := forkOptionsTask.value,
    selectMainClass := mainClass.value orElse askForMainClass(discoveredMainClasses.value),
    mainClass in run := (selectMainClass in run).value,
    mainClass := {
      val logWarning = state.value.currentCommand
        .flatMap(_.commandLine.split(" ").headOption.map(_.trim))
        .fold(true) {
          case "run" | "runMain" => false
          case _                 => true
        }
      pickMainClassOrWarn(discoveredMainClasses.value, streams.value.log, logWarning)
    },
    runMain := foregroundRunMainTask.evaluated,
    run := foregroundRunTask.evaluated,
    fgRun := runTask(fullClasspath, mainClass in run, runner in run).evaluated,
    fgRunMain := runMainTask(fullClasspath, runner in run).evaluated,
    copyResources := copyResourcesTask.value,
    // note that we use the same runner and mainClass as plain run
    mainBgRunMainTaskForConfig(This),
    mainBgRunTaskForConfig(This)
  ) ++ inTask(run)(runnerSettings ++ newRunnerSettings)

  private[this] lazy val configGlobal = globalDefaults(
    Seq(
      initialCommands :== "",
      cleanupCommands :== "",
      asciiGraphWidth :== 40
    )
  )

  lazy val projectTasks: Seq[Setting[_]] = Seq(
    cleanFiles := cleanFilesTask.value,
    cleanKeepFiles := Vector.empty,
    cleanKeepGlobs ++= historyPath.value.map(_.toGlob).toVector,
    clean := Def.taskDyn(Clean.task(resolvedScoped.value.scope, full = true)).value,
    consoleProject := consoleProjectTask.value,
    transitiveDynamicInputs := WatchTransitiveDependencies.task.value,
  ) ++ sbt.internal.DeprecatedContinuous.taskDefinitions

  def generate(generators: SettingKey[Seq[Task[Seq[File]]]]): Initialize[Task[Seq[File]]] =
    generators { _.join.map(_.flatten) }

  @deprecated(
    "The watchTransitiveSourcesTask is used only for legacy builds and will be removed in a future version of sbt.",
    "1.3.0"
  )
  def watchTransitiveSourcesTask: Initialize[Task[Seq[Source]]] =
    watchTransitiveSourcesTaskImpl(watchSources)

  private def watchTransitiveSourcesTaskImpl(
      key: TaskKey[Seq[Source]]
  ): Initialize[Task[Seq[Source]]] = {
    import ScopeFilter.Make.{ inDependencies => inDeps, _ }
    val selectDeps = ScopeFilter(inAggregates(ThisProject) || inDeps(ThisProject))
    val allWatched = (key ?? Nil).all(selectDeps)
    Def.task { allWatched.value.flatten }
  }

  def transitiveUpdateTask: Initialize[Task[Seq[UpdateReport]]] = {
    import ScopeFilter.Make.{ inDependencies => inDeps, _ }
    val selectDeps = ScopeFilter(inDeps(ThisProject, includeRoot = false))
    val allUpdates = update.?.all(selectDeps)
    // If I am a "build" (a project inside project/) then I have a globalPluginUpdate.
    Def.task { allUpdates.value.flatten ++ globalPluginUpdate.?.value }
  }

  @deprecated("This is no longer used to implement continuous execution", "1.3.0")
  def watchSetting: Initialize[Watched] =
    Def.setting {
      val getService = watchService.value
      val interval = pollInterval.value
      val _antiEntropy = watchAntiEntropy.value
      val base = thisProjectRef.value
      val msg = watchingMessage.?.value.getOrElse(Watched.defaultWatchingMessage)
      val trigMsg = triggeredMessage.?.value.getOrElse(Watched.defaultTriggeredMessage)
      new Watched {
        val scoped = watchTransitiveSources in base
        val key = scoped.scopedKey
        override def antiEntropy: FiniteDuration = _antiEntropy
        override def pollInterval: FiniteDuration = interval
        override def watchingMessage(s: WatchState) = msg(s)
        override def triggeredMessage(s: WatchState) = trigMsg(s)
        override def watchService() = getService()
        override def watchSources(s: State) =
          EvaluateTask(Project structure s, key, s, base) match {
            case Some((_, Value(ps))) => ps
            case Some((_, Inc(i)))    => throw i
            case None                 => sys.error("key not found: " + Def.displayFull(key))
          }
      }
    }

  def scalaInstanceTask: Initialize[Task[ScalaInstance]] = Def.taskDyn {
    // if this logic changes, ensure that `unmanagedScalaInstanceOnly` and `update` are changed
    //  appropriately to avoid cycles
    scalaHome.value match {
      case Some(h) => scalaInstanceFromHome(h)
      case None =>
        val scalaProvider = appConfiguration.value.provider.scalaProvider
        val version = scalaVersion.value
        if (version == scalaProvider.version) // use the same class loader as the Scala classes used by sbt
          Def.task {
            val allJars = scalaProvider.jars
            val libraryJars = allJars.filter(_.getName == "scala-library.jar")
            allJars.filter(_.getName == "scala-compiler.jar") match {
              case Array(compilerJar) if libraryJars.nonEmpty =>
                val cache = state.value.extendedClassLoaderCache
                mkScalaInstance(
                  version,
                  allJars,
                  libraryJars,
                  compilerJar,
                  cache,
                  scalaInstanceTopLoader.value
                )
              case _ => ScalaInstance(version, scalaProvider)
            }
          } else
          scalaInstanceFromUpdate
    }
  }

  // Returns the ScalaInstance only if it was not constructed via `update`
  //  This is necessary to prevent cycles between `update` and `scalaInstance`
  private[sbt] def unmanagedScalaInstanceOnly: Initialize[Task[Option[ScalaInstance]]] =
    Def.taskDyn {
      if (scalaHome.value.isDefined) Def.task(Some(scalaInstance.value)) else Def.task(None)
    }

  private[this] def noToolConfiguration(autoInstance: Boolean): String = {
    val pre = "Missing Scala tool configuration from the 'update' report.  "
    val post =
      if (autoInstance)
        "'scala-tool' is normally added automatically, so this may indicate a bug in sbt or you may be removing it from ivyConfigurations, for example."
      else
        "Explicitly define scalaInstance or scalaHome or include Scala dependencies in the 'scala-tool' configuration."
    pre + post
  }

  def scalaInstanceFromUpdate: Initialize[Task[ScalaInstance]] = Def.task {
    val toolReport = update.value.configuration(Configurations.ScalaTool) getOrElse
      sys.error(noToolConfiguration(managedScalaInstance.value))
    def files(id: String) =
      for {
        m <- toolReport.modules if m.module.name == id;
        (art, file) <- m.artifacts if art.`type` == Artifact.DefaultType
      } yield file
    def file(id: String) = files(id).headOption getOrElse sys.error(s"Missing ${id}.jar")
    val allJars = toolReport.modules.flatMap(_.artifacts.map(_._2))
    val libraryJar = file(ScalaArtifacts.LibraryID)
    val compilerJar = file(ScalaArtifacts.CompilerID)
    mkScalaInstance(
      scalaVersion.value,
      allJars,
      Array(libraryJar),
      compilerJar,
      state.value.extendedClassLoaderCache,
      scalaInstanceTopLoader.value,
    )
  }
  private[this] def mkScalaInstance(
      version: String,
      allJars: Seq[File],
      libraryJars: Array[File],
      compilerJar: File,
      classLoaderCache: ClassLoaderCache,
      topLoader: ClassLoader,
  ): ScalaInstance = {
    val allJarsDistinct = allJars.distinct
    val libraryLoader = classLoaderCache(libraryJars.toList, topLoader)
    val fullLoader = classLoaderCache(allJarsDistinct.toList, libraryLoader)
    new ScalaInstance(
      version,
      fullLoader,
      libraryLoader,
      libraryJars,
      compilerJar,
      allJarsDistinct.toArray,
      Some(version)
    )
  }
  def scalaInstanceFromHome(dir: File): Initialize[Task[ScalaInstance]] = Def.task {
    val dummy = ScalaInstance(dir)(state.value.classLoaderCache.apply)
    Seq(dummy.loader, dummy.loaderLibraryOnly).foreach {
      case a: AutoCloseable => a.close()
      case _                =>
    }
    mkScalaInstance(
      dummy.version,
      dummy.allJars,
      dummy.libraryJars,
      dummy.compilerJar,
      state.value.extendedClassLoaderCache,
      scalaInstanceTopLoader.value,
    )
  }

  private[this] def testDefaults =
    Defaults.globalDefaults(
      Seq(
        testFrameworks :== {
          import sbt.TestFrameworks._
          Seq(ScalaCheck, Specs2, Specs, ScalaTest, JUnit)
        },
        testListeners :== Nil,
        testOptions :== Nil,
        testResultLogger :== TestResultLogger.Default,
        testFilter in testOnly :== (selectedFilter _)
      )
    )
  lazy val testTasks
      : Seq[Setting[_]] = testTaskOptions(test) ++ testTaskOptions(testOnly) ++ testTaskOptions(
    testQuick
  ) ++ testDefaults ++ Seq(
    testLoader := ClassLoaders.testTask.value,
    loadedTestFrameworks := {
      val loader = testLoader.value
      val log = streams.value.log
      testFrameworks.value.flatMap(f => f.create(loader, log).map(x => (f, x)).toIterable).toMap
    },
    definedTests := detectTests.value,
    definedTestNames := (definedTests map (_.map(_.name).distinct) storeAs definedTestNames triggeredBy compile).value,
    testFilter in testQuick := testQuickFilter.value,
    executeTests := (
      Def.taskDyn {
        allTestGroupsTask(
          (streams in test).value,
          loadedTestFrameworks.value,
          testLoader.value,
          (testGrouping in test).value,
          (testExecution in test).value,
          (fullClasspath in test).value,
          testForkedParallel.value,
          (javaOptions in test).value,
          (classLoaderLayeringStrategy).value,
          projectId = s"${thisProject.value.id} / ",
        )
      }
    ).value,
    // ((streams in test, loadedTestFrameworks, testLoader, testGrouping in test, testExecution in test, fullClasspath in test, javaHome in test, testForkedParallel, javaOptions in test) flatMap allTestGroupsTask).value,
    testResultLogger in (Test, test) :== TestResultLogger.SilentWhenNoTests, // https://github.com/sbt/sbt/issues/1185
    test := {
      val trl = (testResultLogger in (Test, test)).value
      val taskName = Project.showContextKey(state.value).show(resolvedScoped.value)
      try trl.run(streams.value.log, executeTests.value, taskName)
      finally close(testLoader.value)
    },
    testOnly := {
      try inputTests(testOnly).evaluated
      finally close(testLoader.value)
    },
    testQuick := {
      try inputTests(testQuick).evaluated
      finally close(testLoader.value)
    }
  )
  private def close(sbtLoader: ClassLoader): Unit = sbtLoader match {
    case u: AutoCloseable   => u.close()
    case c: ClasspathFilter => c.close()
    case _                  =>
  }

  /**
   * A scope whose task axis is set to Zero.
   */
  lazy val TaskZero: Scope = ThisScope.copy(task = Zero)
  lazy val TaskGlobal: Scope = TaskZero

  /**
   * A scope whose configuration axis is set to Zero.
   */
  lazy val ConfigZero: Scope = ThisScope.copy(config = Zero)
  lazy val ConfigGlobal: Scope = ConfigZero
  def testTaskOptions(key: Scoped): Seq[Setting[_]] =
    inTask(key)(
      Seq(
        testListeners := {
          val stateLogLevel = state.value.get(Keys.logLevel.key).getOrElse(Level.Info)
          TestLogger.make(
            streams.value.log,
            closeableTestLogger(
              streamsManager.value,
              test in resolvedScoped.value.scope,
              logBuffered.value
            ),
            Keys.logLevel.?.value.getOrElse(stateLogLevel),
          ) +:
            new TestStatusReporter(succeededFile(streams.in(test).value.cacheDirectory)) +:
            testListeners.in(TaskZero).value
        },
        testOptions := Tests.Listeners(testListeners.value) +: (testOptions in TaskZero).value,
        testExecution := testExecutionTask(key).value
      )
    ) ++ inScope(GlobalScope)(
      Seq(
        derive(testGrouping := singleTestGroupDefault.value)
      )
    )

  private[this] def closeableTestLogger(manager: Streams, baseKey: Scoped, buffered: Boolean)(
      tdef: TestDefinition
  ): TestLogger.PerTest = {
    val scope = baseKey.scope
    val extra = scope.extra match { case Select(x) => x; case _ => AttributeMap.empty }
    val key = ScopedKey(scope.copy(extra = Select(testExtra(extra, tdef))), baseKey.key)
    val s = manager(key)
    new TestLogger.PerTest(s.log, () => s.close(), buffered)
  }

  def testExtra(extra: AttributeMap, tdef: TestDefinition): AttributeMap = {
    val mod = tdef.fingerprint match {
      case f: SubclassFingerprint  => f.isModule
      case f: AnnotatedFingerprint => f.isModule
      case _                       => false
    }
    extra.put(name.key, tdef.name).put(isModule, mod)
  }

  def singleTestGroup(key: Scoped): Initialize[Task[Seq[Tests.Group]]] =
    inTask(key, singleTestGroupDefault)
  def singleTestGroupDefault: Initialize[Task[Seq[Tests.Group]]] = Def.task {
    val tests = definedTests.value
    val fk = fork.value
    val opts = forkOptions.value
    Seq(
      new Tests.Group(
        "<default>",
        tests,
        if (fk) Tests.SubProcess(opts) else Tests.InProcess,
        Seq.empty
      )
    )
  }
  def forkOptionsTask: Initialize[Task[ForkOptions]] =
    Def.task {
      ForkOptions(
        javaHome = javaHome.value,
        outputStrategy = outputStrategy.value,
        // bootJars is empty by default because only jars on the user's classpath should be on the boot classpath
        bootJars = Vector(),
        workingDirectory = Some(baseDirectory.value),
        runJVMOptions = javaOptions.value.toVector,
        connectInput = connectInput.value,
        envVars = envVars.value
      )
    }

  def testExecutionTask(task: Scoped): Initialize[Task[Tests.Execution]] =
    Def.task {
      new Tests.Execution(
        (testOptions in task).value,
        (parallelExecution in task).value,
        (tags in task).value
      )
    }

  def testQuickFilter: Initialize[Task[Seq[String] => Seq[String => Boolean]]] =
    Def.task {
      val cp = (fullClasspath in test).value
      val s = (streams in test).value
      val ans: Seq[Analysis] = cp.flatMap(_.metadata get Keys.analysis) map {
        case a0: Analysis => a0
      }
      val succeeded = TestStatus.read(succeededFile(s.cacheDirectory))
      val stamps = collection.mutable.Map.empty[String, Long]
      def stamp(dep: String): Long = {
        val stamps = for (a <- ans) yield intlStamp(dep, a, Set.empty)
        if (stamps.isEmpty) Long.MinValue
        else stamps.max
      }
      def intlStamp(c: String, analysis: Analysis, s: Set[String]): Long = {
        if (s contains c) Long.MinValue
        else
          stamps.getOrElse(
            c, {
              val x = {
                import analysis.{ apis, relations => rel }
                rel.internalClassDeps(c).map(intlStamp(_, analysis, s + c)) ++
                  rel.externalDeps(c).map(stamp) +
                  (apis.internal.get(c) match {
                    case Some(x) => x.compilationTimestamp
                    case _       => Long.MinValue
                  })
              }.max
              if (x != Long.MinValue) {
                stamps(c) = x
              }
              x
            }
          )
      }
      def noSuccessYet(test: String) = succeeded.get(test) match {
        case None     => true
        case Some(ts) => stamp(test) > ts
      }
      args =>
        for (filter <- selectedFilter(args))
          yield (test: String) => filter(test) && noSuccessYet(test)
    }
  def succeededFile(dir: File) = dir / "succeeded_tests"

  def inputTests(key: InputKey[_]): Initialize[InputTask[Unit]] =
    inputTests0.mapReferenced(Def.mapScope(_ in key.key))
  private[this] lazy val inputTests0: Initialize[InputTask[Unit]] = {
    val parser = loadForParser(definedTestNames)((s, i) => testOnlyParser(s, i getOrElse Nil))
    Def.inputTaskDyn {
      val (selected, frameworkOptions) = parser.parsed
      val s = streams.value
      val filter = testFilter.value
      val config = testExecution.value

      implicit val display = Project.showContextKey(state.value)
      val modifiedOpts = Tests.Filters(filter(selected)) +: Tests.Argument(frameworkOptions: _*) +: config.options
      val newConfig = config.copy(options = modifiedOpts)
      val output = allTestGroupsTask(
        s,
        loadedTestFrameworks.value,
        testLoader.value,
        testGrouping.value,
        newConfig,
        fullClasspath.value,
        testForkedParallel.value,
        javaOptions.value,
        classLoaderLayeringStrategy.value,
        projectId = s"${thisProject.value.id} / ",
      )
      val taskName = display.show(resolvedScoped.value)
      val trl = testResultLogger.value
      output.map(out => trl.run(s.log, out, taskName))
    }
  }

  def createTestRunners(
      frameworks: Map[TestFramework, Framework],
      loader: ClassLoader,
      config: Tests.Execution
  ): Map[TestFramework, Runner] = {
    import Tests.Argument
    val opts = config.options.toList
    frameworks.map {
      case (tf, f) =>
        val args = opts.flatMap {
          case Argument(None | Some(`tf`), args) => args
          case _                                 => Nil
        }
        val mainRunner = f.runner(args.toArray, Array.empty[String], loader)
        tf -> mainRunner
    }
  }

  private[sbt] def allTestGroupsTask(
      s: TaskStreams,
      frameworks: Map[TestFramework, Framework],
      loader: ClassLoader,
      groups: Seq[Tests.Group],
      config: Tests.Execution,
      cp: Classpath,
  ): Initialize[Task[Tests.Output]] = {
    allTestGroupsTask(
      s,
      frameworks,
      loader,
      groups,
      config,
      cp,
      forkedParallelExecution = false,
      javaOptions = Nil,
      strategy = ClassLoaderLayeringStrategy.ScalaLibrary,
      projectId = "",
    )
  }

  private[sbt] def allTestGroupsTask(
      s: TaskStreams,
      frameworks: Map[TestFramework, Framework],
      loader: ClassLoader,
      groups: Seq[Tests.Group],
      config: Tests.Execution,
      cp: Classpath,
      forkedParallelExecution: Boolean
  ): Initialize[Task[Tests.Output]] = {
    allTestGroupsTask(
      s,
      frameworks,
      loader,
      groups,
      config,
      cp,
      forkedParallelExecution,
      javaOptions = Nil,
      strategy = ClassLoaderLayeringStrategy.ScalaLibrary,
      projectId = "",
    )
  }

  private[sbt] def allTestGroupsTask(
      s: TaskStreams,
      frameworks: Map[TestFramework, Framework],
      loader: ClassLoader,
      groups: Seq[Tests.Group],
      config: Tests.Execution,
      cp: Classpath,
      forkedParallelExecution: Boolean,
      javaOptions: Seq[String],
      strategy: ClassLoaderLayeringStrategy,
      projectId: String
  ): Initialize[Task[Tests.Output]] = {
    val runners = createTestRunners(frameworks, loader, config)
    val groupTasks = groups map { group =>
      group.runPolicy match {
        case Tests.SubProcess(opts) =>
          s.log.debug(s"javaOptions: ${opts.runJVMOptions}")
          val forkedConfig = config.copy(parallel = config.parallel && forkedParallelExecution)
          s.log.debug(s"Forking tests - parallelism = ${forkedConfig.parallel}")
          ForkTests(
            runners,
            group.tests.toVector,
            forkedConfig,
            cp.files,
            opts,
            s.log,
            (Tags.ForkedTestGroup, 1) +: group.tags: _*
          )
        case Tests.InProcess =>
          if (javaOptions.nonEmpty) {
            s.log.warn("javaOptions will be ignored, fork is set to false")
          }
          Tests(
            frameworks,
            loader,
            runners,
            group.tests.toVector,
            config.copy(tags = config.tags ++ group.tags),
            s.log
          )
      }
    }
    val output = Tests.foldTasks(groupTasks, config.parallel)
    val result = output map { out =>
      out.events.foreach {
        case (suite, e) =>
          if (strategy != ClassLoaderLayeringStrategy.Flat ||
              strategy != ClassLoaderLayeringStrategy.ScalaLibrary) {
            (e.throwables ++ e.throwables.flatMap(t => Option(t.getCause)))
              .find { t =>
                t.isInstanceOf[NoClassDefFoundError] ||
                t.isInstanceOf[IllegalAccessError] ||
                t.isInstanceOf[ClassNotFoundException]
              }
              .foreach { t =>
                s.log.error(
                  s"Test suite $suite failed with $t.\nThis may be due to the "
                    + s"ClassLoaderLayeringStrategy ($strategy) used by your task.\n"
                    + "To improve performance and reduce memory, sbt attempts to cache the"
                    + " class loaders used to load the project dependencies.\n"
                    + "The project class files are loaded in a separate class loader that is"
                    + " created for each test run.\nThe test class loader accesses the project"
                    + " dependency classes using the cached project dependency classloader.\nWith"
                    + " this approach, class loading may fail under the following conditions:\n\n"
                    + " * Dependencies use reflection to access classes in your project's"
                    + " classpath.\n   Java serialization/deserialization may cause this.\n"
                    + " * An open package is accessed across layers. If the project's classes"
                    + " access or extend\n   jvm package private classes defined in a"
                    + " project dependency, it may cause an IllegalAccessError\n   because the"
                    + " jvm enforces package private at the classloader level.\n\n"
                    + "These issues, along with others that were not enumerated above, may be"
                    + " resolved by changing the class loader layering strategy.\n"
                    + "The Flat and ScalaLibrary strategies bundle the full project classpath in"
                    + " the same class loader.\nTo use one of these strategies, set the "
                    + " ClassLoaderLayeringStrategy key\nin your configuration, for example:\n\n"
                    + s"set ${projectId}Test / classLoaderLayeringStrategy :="
                    + " ClassLoaderLayeringStrategy.ScalaLibrary\n"
                    + s"set ${projectId}Test / classLoaderLayeringStrategy :="
                    + " ClassLoaderLayeringStrategy.Flat\n\n"
                    + "See ClassLoaderLayeringStrategy.scala for the full list of options."
                )
              }
          }
      }
      val summaries =
        runners map {
          case (tf, r) =>
            Tests.Summary(frameworks(tf).name, r.done())
        }
      out.copy(summaries = summaries)
    }
    Def.value { result }
  }

  def selectedFilter(args: Seq[String]): Seq[String => Boolean] = {
    def matches(nfs: Seq[NameFilter], s: String) = nfs.exists(_.accept(s))

    val (excludeArgs, includeArgs) = args.partition(_.startsWith("-"))

    val includeFilters = includeArgs map GlobFilter.apply
    val excludeFilters = excludeArgs.map(_.substring(1)).map(GlobFilter.apply)

    (includeFilters, excludeArgs) match {
      case (Nil, Nil) => Seq(const(true))
      case (Nil, _)   => Seq((s: String) => !matches(excludeFilters, s))
      case _ =>
        includeFilters.map(f => (s: String) => (f.accept(s) && !matches(excludeFilters, s)))
    }
  }
  def detectTests: Initialize[Task[Seq[TestDefinition]]] =
    Def.task {
      Tests.discover(loadedTestFrameworks.value.values.toList, compile.value, streams.value.log)._1
    }
  def defaultRestrictions: Initialize[Seq[Tags.Rule]] =
    Def.setting {
      val par = parallelExecution.value
      val max = EvaluateTask.SystemProcessors
      Tags.limitAll(if (par) max else 1) ::
        Tags.limit(Tags.ForkedTestGroup, 1) ::
        Tags.exclusiveGroup(Tags.Clean) ::
        Nil
    }

  lazy val packageBase: Seq[Setting[_]] = Seq(
    artifact := Artifact(moduleName.value)
  ) ++ Defaults.globalDefaults(
    Seq(
      packageOptions :== Nil,
      artifactName :== (Artifact.artifactName _)
    )
  )

  lazy val packageConfig: Seq[Setting[_]] =
    inTask(packageBin)(
      Seq(
        packageOptions := {
          val n = name.value
          val ver = version.value
          val org = organization.value
          val orgName = organizationName.value
          val main = mainClass.value
          val old = packageOptions.value
          Package.addSpecManifestAttributes(n, ver, orgName) +:
            Package.addImplManifestAttributes(n, ver, homepage.value, org, orgName) +:
            main.map(Package.MainClass.apply) ++: old
        }
      )
    ) ++
      inTask(packageSrc)(
        Seq(
          packageOptions := Package.addSpecManifestAttributes(
            name.value,
            version.value,
            organizationName.value
          ) +: packageOptions.value
        )
      ) ++
      packageTaskSettings(packageBin, packageBinMappings) ++
      packageTaskSettings(packageSrc, packageSrcMappings) ++
      packageTaskSettings(packageDoc, packageDocMappings) ++
      Seq(Keys.`package` := packageBin.value)

  def packageBinMappings = products map { _ flatMap Path.allSubpaths }
  def packageDocMappings = doc map { Path.allSubpaths(_).toSeq }
  def packageSrcMappings = concatMappings(resourceMappings, sourceMappings)

  private type Mappings = Initialize[Task[Seq[(File, String)]]]
  def concatMappings(as: Mappings, bs: Mappings) =
    (as zipWith bs)((a, b) => (a, b) map { case (a, b) => a ++ b })

  // drop base directories, since there are no valid mappings for these
  def sourceMappings: Initialize[Task[Seq[(File, String)]]] =
    Def.task {
      val sdirs = unmanagedSourceDirectories.value
      val base = baseDirectory.value
      val relative = (f: File) => relativeTo(sdirs)(f).orElse(relativeTo(base)(f)).orElse(flat(f))
      val exclude = Set(sdirs, base)
      unmanagedSources.value.flatMap {
        case s if !exclude(s) => relative(s).map(s -> _)
        case _                => None
      }
    }

  def resourceMappings = relativeMappings(unmanagedResources, unmanagedResourceDirectories)

  def relativeMappings(
      files: Taskable[Seq[File]],
      dirs: Taskable[Seq[File]]
  ): Initialize[Task[Seq[(File, String)]]] =
    Def.task {
      val rdirs = dirs.toTask.value.toSet
      val relative = (f: File) => relativeTo(rdirs)(f).orElse(flat(f))
      files.toTask.value.flatMap {
        case r if !rdirs(r) => relative(r).map(r -> _)
        case _              => None
      }
    }

  def collectFiles(
      dirs: Taskable[Seq[File]],
      filter: Taskable[FileFilter],
      excludes: Taskable[FileFilter]
  ): Initialize[Task[Seq[File]]] =
    Def.task {
      dirs.toTask.value.descendantsExcept(filter.toTask.value, excludes.toTask.value).get
    }

  def relativeMappings( // forward to widened variant
      files: ScopedTaskable[Seq[File]],
      dirs: ScopedTaskable[Seq[File]]
  ): Initialize[Task[Seq[(File, String)]]] = relativeMappings(files: Taskable[Seq[File]], dirs)

  def collectFiles( // forward to widened variant
      dirs: ScopedTaskable[Seq[File]],
      filter: ScopedTaskable[FileFilter],
      excludes: ScopedTaskable[FileFilter]
  ): Initialize[Task[Seq[File]]] = collectFiles(dirs: Taskable[Seq[File]], filter, excludes)

  private[sbt] def earlyArtifactPathSetting(art: SettingKey[Artifact]): Initialize[File] =
    Def.setting {
      val f = artifactName.value
      crossTarget.value / "early" / f(
        ScalaVersion(
          (scalaVersion in artifactName).value,
          (scalaBinaryVersion in artifactName).value
        ),
        projectID.value,
        art.value
      )
    }

  def artifactPathSetting(art: SettingKey[Artifact]): Initialize[File] =
    Def.setting {
      val f = artifactName.value
      crossTarget.value / f(
        ScalaVersion(
          (scalaVersion in artifactName).value,
          (scalaBinaryVersion in artifactName).value
        ),
        projectID.value,
        art.value
      )
    }

  def artifactSetting: Initialize[Artifact] =
    Def.setting {
      val a = artifact.value
      val classifier = artifactClassifier.value
      val cOpt = configuration.?.value
      val cPart = cOpt flatMap {
        case Compile => None
        case Test    => Some(Artifact.TestsClassifier)
        case c       => Some(c.name)
      }
      val combined = cPart.toList ++ classifier.toList
      val configurations = cOpt.map(c => ConfigRef(c.name)).toVector
      if (combined.isEmpty) a.withClassifier(None).withConfigurations(configurations)
      else {
        val a1 = a
          .withClassifier(Some(combined.mkString("-")))
          .withConfigurations(configurations)
        // use "source" as opposed to "foo-source" to retrieve the type
        classifier match {
          case Some(c) => a1.withType(Artifact.classifierType(c))
          case None    => a1
        }
      }
    }

  @deprecated("The configuration(s) should not be decided based on the classifier.", "1.0.0")
  def artifactConfigurations(
      base: Artifact,
      scope: Configuration,
      classifier: Option[String]
  ): Iterable[Configuration] =
    classifier match {
      case Some(c) => Artifact.classifierConf(c) :: Nil
      case None    => scope :: Nil
    }

  def packageTaskSettings(key: TaskKey[File], mappingsTask: Initialize[Task[Seq[(File, String)]]]) =
    inTask(key)(
      Seq(
        key in TaskZero := packageTask.value,
        packageConfiguration := packageConfigurationTask.value,
        mappings := mappingsTask.value,
        packagedArtifact := (artifact.value -> key.value),
        artifact := artifactSetting.value,
        artifactPath := artifactPathSetting(artifact).value
      )
    )

  def packageTask: Initialize[Task[File]] =
    Def.task {
      val config = packageConfiguration.value
      val s = streams.value
      Package(
        config,
        s.cacheStoreFactory,
        s.log,
        sys.env.get("SOURCE_DATE_EPOCH").map(_.toLong * 1000).orElse(Some(0L))
      )
      config.jar
    }

  def packageConfigurationTask: Initialize[Task[Package.Configuration]] =
    Def.task {
      new Package.Configuration(mappings.value, artifactPath.value, packageOptions.value)
    }

  def askForMainClass(classes: Seq[String]): Option[String] =
    sbt.SelectMainClass(
      if (classes.length >= 10) Some(SimpleReader(Terminal.get).readLine(_))
      else
        Some(s => {
          def print(st: String) = { scala.Console.out.print(st); scala.Console.out.flush() }
          print(s)
          Terminal.get.withRawInput {
            try Terminal.get.inputStream.read match {
              case -1 | -2 => None
              case b =>
                val res = b.toChar.toString
                println(res)
                Some(res)
            } catch { case e: InterruptedException => None }
          }
        }),
      classes
    )

  def pickMainClass(classes: Seq[String]): Option[String] =
    sbt.SelectMainClass(None, classes)

  private def pickMainClassOrWarn(
      classes: Seq[String],
      logger: Logger,
      logWarning: Boolean
  ): Option[String] = {
    classes match {
      case multiple if multiple.size > 1 && logWarning =>
        val msg =
          "multiple main classes detected: run 'show discoveredMainClasses' to see the list"
        logger.warn(msg)
      case _ =>
    }
    pickMainClass(classes)
  }

  /** Implements `cleanFiles` task. */
  private[sbt] def cleanFilesTask: Initialize[Task[Vector[File]]] = Def.task { Vector.empty[File] }

  def bgRunMainTask(
      products: Initialize[Task[Classpath]],
      classpath: Initialize[Task[Classpath]],
      copyClasspath: Initialize[Boolean],
      scalaRun: Initialize[Task[ScalaRun]]
  ): Initialize[InputTask[JobHandle]] = {
    val parser = Defaults.loadForParser(discoveredMainClasses)(
      (s, names) => Defaults.runMainParser(s, names getOrElse Nil)
    )
    Def.inputTask {
      val service = bgJobService.value
      val (mainClass, args) = parser.parsed
      val hashClasspath = (bgHashClasspath in bgRunMain).value
      service.runInBackgroundWithLoader(resolvedScoped.value, state.value) { (logger, workingDir) =>
        val files =
          if (copyClasspath.value)
            service.copyClasspath(products.value, classpath.value, workingDir, hashClasspath)
          else classpath.value
        val cp = data(files)
        scalaRun.value match {
          case r: Run =>
            val loader = r.newLoader(cp)
            (Some(loader), () => r.runWithLoader(loader, cp, mainClass, args, logger).get)
          case sr =>
            (None, () => sr.run(mainClass, cp, args, logger).get)
        }
      }
    }
  }

  def bgRunTask(
      products: Initialize[Task[Classpath]],
      classpath: Initialize[Task[Classpath]],
      mainClassTask: Initialize[Task[Option[String]]],
      copyClasspath: Initialize[Boolean],
      scalaRun: Initialize[Task[ScalaRun]]
  ): Initialize[InputTask[JobHandle]] = {
    import Def.parserToInput
    val parser = Def.spaceDelimited()
    Def.inputTask {
      val service = bgJobService.value
      val mainClass = mainClassTask.value getOrElse sys.error("No main class detected.")
      val hashClasspath = (bgHashClasspath in bgRun).value
      service.runInBackgroundWithLoader(resolvedScoped.value, state.value) { (logger, workingDir) =>
        val files =
          if (copyClasspath.value)
            service.copyClasspath(products.value, classpath.value, workingDir, hashClasspath)
          else classpath.value
        val cp = data(files)
        val args = parser.parsed
        scalaRun.value match {
          case r: Run =>
            val loader = r.newLoader(cp)
            (Some(loader), () => r.runWithLoader(loader, cp, mainClass, args, logger).get)
          case sr =>
            (None, () => sr.run(mainClass, cp, args, logger).get)
        }
      }
    }
  }

  // runMain calls bgRunMain in the background and waits for the result.
  def foregroundRunMainTask: Initialize[InputTask[Unit]] =
    Def.inputTask {
      val handle = bgRunMain.evaluated
      val service = bgJobService.value
      service.waitForTry(handle).get
    }

  // run calls bgRun in the background and waits for the result.
  def foregroundRunTask: Initialize[InputTask[Unit]] =
    Def.inputTask {
      val handle = bgRun.evaluated
      val service = bgJobService.value
      service.waitForTry(handle).get
    }

  def runMainTask(
      classpath: Initialize[Task[Classpath]],
      scalaRun: Initialize[Task[ScalaRun]]
  ): Initialize[InputTask[Unit]] = {
    val parser =
      loadForParser(discoveredMainClasses)((s, names) => runMainParser(s, names getOrElse Nil))
    Def.inputTask {
      val (mainClass, args) = parser.parsed
      scalaRun.value.run(mainClass, data(classpath.value), args, streams.value.log).get
    }
  }

  def runTask(
      classpath: Initialize[Task[Classpath]],
      mainClassTask: Initialize[Task[Option[String]]],
      scalaRun: Initialize[Task[ScalaRun]]
  ): Initialize[InputTask[Unit]] = {
    import Def.parserToInput
    val parser = Def.spaceDelimited()
    Def.inputTask {
      val mainClass = mainClassTask.value getOrElse sys.error("No main class detected.")
      scalaRun.value.run(mainClass, data(classpath.value), parser.parsed, streams.value.log).get
    }
  }

  def runnerTask: Setting[Task[ScalaRun]] = runner := runnerInit.value

  def runnerInit: Initialize[Task[ScalaRun]] = Def.task {
    val tmp = taskTemporaryDirectory.value
    val resolvedScope = resolvedScoped.value.scope
    val si = scalaInstance.value
    val s = streams.value
    val opts = forkOptions.value
    val options = javaOptions.value
    val trap = trapExit.value
    if (fork.value) {
      s.log.debug(s"javaOptions: $options")
      new ForkRun(opts)
    } else {
      if (options.nonEmpty) {
        val mask = ScopeMask(project = false)
        val showJavaOptions = Scope.displayMasked(
          (javaOptions in resolvedScope).scopedKey.scope,
          (javaOptions in resolvedScope).key.label,
          mask
        )
        val showFork = Scope.displayMasked(
          (fork in resolvedScope).scopedKey.scope,
          (fork in resolvedScope).key.label,
          mask
        )
        s.log.warn(s"$showJavaOptions will be ignored, $showFork is set to false")
      }
      new Run(si, trap, tmp)
    }
  }

  private def foreachJobTask(
      f: (BackgroundJobService, JobHandle) => Unit
  ): Initialize[InputTask[Unit]] = {
    val parser: Initialize[State => Parser[Seq[JobHandle]]] = Def.setting { (s: State) =>
      val extracted = Project.extract(s)
      val service = extracted.get(bgJobService)
      // you might be tempted to use the jobList task here, but the problem
      // is that its result gets cached during execution and therefore stale
      BackgroundJobService.jobIdParser(s, service.jobs)
    }
    Def.inputTask {
      val handles = parser.parsed
      for (handle <- handles) {
        f(bgJobService.value, handle)
      }
    }
  }

  def psTask: Initialize[Task[Seq[JobHandle]]] =
    Def.task {
      val xs = bgList.value
      val s = streams.value
      xs foreach { x =>
        s.log.info(x.toString)
      }
      xs
    }

  def bgStopTask: Initialize[InputTask[Unit]] = foreachJobTask { (manager, handle) =>
    manager.stop(handle)
  }

  def bgWaitForTask: Initialize[InputTask[Unit]] = foreachJobTask { (manager, handle) =>
    manager.waitForTry(handle)
    ()
  }

  def docTaskSettings(key: TaskKey[File] = doc): Seq[Setting[_]] =
    inTask(key)(
      Seq(
        apiMappings ++= {
          val dependencyCp = dependencyClasspath.value
          val log = streams.value.log
          if (autoAPIMappings.value) APIMappings.extract(dependencyCp, log).toMap
          else Map.empty[File, URL]
        },
        fileInputOptions := Seq("-doc-root-content", "-diagrams-dot-path"),
        key in TaskZero := {
          val s = streams.value
          val cs: Compilers = compilers.value
          val srcs = sources.value
          val out = target.value
          val sOpts = scalacOptions.value
          val xapis = apiMappings.value
          val hasScala = srcs.exists(_.name.endsWith(".scala"))
          val hasJava = srcs.exists(_.name.endsWith(".java"))
          val cp = data(dependencyClasspath.value).toList
          val label = nameForSrc(configuration.value.name)
          val fiOpts = fileInputOptions.value
          val reporter = (compilerReporter in compile).value
          val converter = fileConverter.value
          (hasScala, hasJava) match {
            case (true, _) =>
              val options = sOpts ++ Opts.doc.externalAPI(xapis)
              val runDoc = Doc.scaladoc(label, s.cacheStoreFactory sub "scala", cs.scalac match {
                case ac: AnalyzingCompiler => ac.onArgs(exported(s, "scaladoc"))
              }, fiOpts)
              runDoc(srcs, cp, out, options, maxErrors.value, s.log)
            case (_, true) =>
              val javadoc =
                sbt.inc.Doc.cachedJavadoc(label, s.cacheStoreFactory sub "java", cs.javaTools)
              javadoc.run(
                srcs.toList map { x =>
                  converter.toVirtualFile(x.toPath)
                },
                cp.toList map { x =>
                  converter.toVirtualFile(x.toPath)
                },
                converter,
                out.toPath,
                javacOptions.value.toList,
                IncToolOptionsUtil.defaultIncToolOptions(),
                s.log,
                reporter
              )
            case _ => () // do nothing
          }
          out
        }
      )
    )

  def mainBgRunTask = mainBgRunTaskForConfig(Select(Runtime))
  def mainBgRunMainTask = mainBgRunMainTaskForConfig(Select(Runtime))

  private[this] def mainBgRunTaskForConfig(c: ScopeAxis[ConfigKey]) =
    bgRun := bgRunTask(
      exportedProductJars,
      fullClasspathAsJars in (This, c, This),
      mainClass in run,
      bgCopyClasspath in bgRun,
      runner in run
    ).evaluated

  private[this] def mainBgRunMainTaskForConfig(c: ScopeAxis[ConfigKey]) =
    bgRunMain := bgRunMainTask(
      exportedProductJars,
      fullClasspathAsJars in (This, c, This),
      bgCopyClasspath in bgRunMain,
      runner in run
    ).evaluated

  def discoverMainClasses(analysis: CompileAnalysis): Seq[String] = analysis match {
    case analysis: Analysis =>
      analysis.infos.allInfos.values.map(_.getMainClasses).flatten.toSeq.sorted
  }

  def consoleProjectTask =
    Def.task {
      ConsoleProject(state.value, (initialCommands in consoleProject).value)(streams.value.log)
      println()
    }

  def consoleTask: Initialize[Task[Unit]] = consoleTask(fullClasspath, console)
  def consoleQuickTask = consoleTask(externalDependencyClasspath, consoleQuick)
  def consoleTask(classpath: TaskKey[Classpath], task: TaskKey[_]): Initialize[Task[Unit]] =
    Def.task {
      val si = (scalaInstance in task).value
      val s = streams.value
      val cpFiles = data((classpath in task).value)
      val fullcp = (cpFiles ++ si.allJars).distinct
      val tempDir = IO.createUniqueDirectory((taskTemporaryDirectory in task).value).toPath
      val loader = ClasspathUtil.makeLoader(fullcp.map(_.toPath), si, tempDir)
      val compiler =
        (compilers in task).value.scalac match {
          case ac: AnalyzingCompiler => ac.onArgs(exported(s, "scala"))
        }
      val sc = (scalacOptions in task).value
      val ic = (initialCommands in task).value
      val cc = (cleanupCommands in task).value
      (new Console(compiler))(cpFiles, sc, loader, ic, cc)()(s.log).get
      println()
    }

  private[this] def exported(w: PrintWriter, command: String): Seq[String] => Unit =
    args => w.println((command +: args).mkString(" "))

  private[this] def exported(s: TaskStreams, command: String): Seq[String] => Unit = {
    val w = s.text(ExportStream)
    try exported(w, command)
    finally w.close() // workaround for #937
  }

  /** Handles traditional Scalac compilation. For non-pipelined compilation,
   *  this also handles Java compilation.
   */
  private[sbt] def compileScalaBackendTask: Initialize[Task[CompileResult]] = Def.task {
    val setup: Setup = compileIncSetup.value
    val useBinary: Boolean = enableBinaryCompileAnalysis.value
    val analysisResult: CompileResult = compileIncremental.value
    val exportP = exportPipelining.value
    // Save analysis midway if pipelining is enabled
    if (analysisResult.hasModified && exportP) {
      val store =
        MixedAnalyzingCompiler.staticCachedStore(setup.cacheFile.toPath, !useBinary)
      val contents = AnalysisContents.create(analysisResult.analysis(), analysisResult.setup())
      store.set(contents)
    }
    analysisResult
  }

  /** Block on earlyOutputPing promise, which will be completed by `compile` midway
   * via `compileProgress` implementation.
   */
  private[sbt] def compileEarlyTask: Initialize[Task[CompileAnalysis]] = Def.task {
    if ({
      streams.value.log
        .debug(s"${name.value}: compileEarly: blocking on earlyOutputPing")
      earlyOutputPing.await.value
    }) {
      val useBinary: Boolean = enableBinaryCompileAnalysis.value
      val store =
        MixedAnalyzingCompiler.staticCachedStore(earlyCompileAnalysisFile.value.toPath, !useBinary)
      store.get.toOption match {
        case Some(contents) => contents.getAnalysis
        case _              => Analysis.empty
      }
    } else {
      compile.value
    }
  }
  def compileTask: Initialize[Task[CompileAnalysis]] = Def.task {
    val setup: Setup = compileIncSetup.value
    val useBinary: Boolean = enableBinaryCompileAnalysis.value
    val c = fileConverter.value
    // TODO - expose bytecode manipulation phase.
    val analysisResult: CompileResult = manipulateBytecode.value
    if (analysisResult.hasModified) {
      val store =
        MixedAnalyzingCompiler.staticCachedStore(setup.cacheFile.toPath, !useBinary)
      val contents = AnalysisContents.create(analysisResult.analysis(), analysisResult.setup())
      store.set(contents)
    }
    val map = managedFileStampCache.value
    val analysis = analysisResult.analysis
    import scala.collection.JavaConverters._
    analysis.readStamps.getAllProductStamps.asScala.foreach {
      case (f: VirtualFileRef, s) =>
        map.put(c.toPath(f), sbt.nio.FileStamp.fromZincStamp(s))
    }
    analysis
  }
  def compileIncrementalTask = Def.task {
    BspCompileTask.compute(bspTargetIdentifier.value, thisProjectRef.value, configuration.value) {
      // TODO - Should readAnalysis + saveAnalysis be scoped by the compile task too?
      compileIncrementalTaskImpl(
        streams.value,
        (compile / compileInputs).value,
        earlyOutputPing.value
      )
    }
  }
  private val incCompiler = ZincUtil.defaultIncrementalCompiler
  private[sbt] def compileJavaTask: Initialize[Task[CompileResult]] = Def.task {
    val s = streams.value
    val in = (compileJava / compileInputs).value
    Def.unit(compileScalaBackend.value)
    try {
      incCompiler.asInstanceOf[sbt.internal.inc.IncrementalCompilerImpl].compileAllJava(in, s.log)
    } finally {
      in.setup.reporter match {
        case r: BuildServerReporter => r.sendFinalReport()
      }
    }
  }
  private[this] def compileIncrementalTaskImpl(
      s: TaskStreams,
      ci: Inputs,
      promise: PromiseWrap[Boolean]
  ): CompileResult = {
    lazy val x = s.text(ExportStream)
    def onArgs(cs: Compilers) = {
      cs.withScalac(
        cs.scalac match {
          case ac: AnalyzingCompiler => ac.onArgs(exported(x, "scalac"))
          case x                     => x
        }
      )
    }
    val compilers: Compilers = ci.compilers
    val i = ci.withCompilers(onArgs(compilers))
    try {
      incCompiler.compile(i, s.log)
    } catch {
      case e: Throwable if !promise.isCompleted =>
        promise.failure(e)
        throw e
    } finally {
      i.setup.reporter match {
        case r: BuildServerReporter => r.sendFinalReport()
      }
      x.close() // workaround for #937
    }
  }
  def compileIncSetupTask = Def.task {
    val cp = dependencyPicklePath.value
    val lookup = new PerClasspathEntryLookup {
      private val cachedAnalysisMap: VirtualFile => Option[CompileAnalysis] =
        analysisMap(cp)
      private val cachedPerEntryDefinesClassLookup: VirtualFile => DefinesClass =
        Keys.classpathEntryDefinesClassVF.value
      override def analysis(classpathEntry: VirtualFile): Optional[CompileAnalysis] =
        cachedAnalysisMap(classpathEntry).toOptional
      override def definesClass(classpathEntry: VirtualFile): DefinesClass =
        cachedPerEntryDefinesClassLookup(classpathEntry)
    }
    val extra = extraIncOptions.value.map(t2)
    Setup.of(
      lookup,
      (skip in compile).value,
      compileAnalysisFile.value.toPath,
      compilerCache.value,
      incOptions.value,
      (compilerReporter in compile).value,
      Some((compile / compileProgress).value).toOptional,
      extra.toArray,
    )
  }
  def compileInputsSettings: Seq[Setting[_]] =
    compileInputsSettings(dependencyPicklePath)
  def compileInputsSettings(classpathTask: TaskKey[VirtualClasspath]): Seq[Setting[_]] = {
    Seq(
      compileOptions := {
        val c = fileConverter.value
        val cp0 = classpathTask.value
        val cp = backendOutput.value +: data(cp0)
        val vs = sources.value.toVector map { x =>
          c.toVirtualFile(x.toPath)
        }
        val eo = CompileOutput(c.toPath(earlyOutput.value))
        val eoOpt =
          if (exportPipelining.value) Some(eo)
          else None
        CompileOptions.of(
          cp.toArray,
          vs.toArray,
          c.toPath(backendOutput.value),
          scalacOptions.value.toArray,
          javacOptions.value.toArray,
          maxErrors.value,
          f1(foldMappers(sourcePositionMappers.value)),
          compileOrder.value,
          None.toOptional: Optional[NioPath],
          Some(fileConverter.value).toOptional,
          Some(reusableStamper.value).toOptional,
          eoOpt.toOptional,
        )
      },
      compilerReporter := {
        new BuildServerReporter(
          bspTargetIdentifier.value,
          maxErrors.value,
          streams.value.log,
          foldMappers(sourcePositionMappers.value),
          sources.value
        )
      },
      compileInputs := {
        val options = compileOptions.value
        val setup = compileIncSetup.value
        Inputs.of(
          compilers.value,
          options,
          setup,
          previousCompile.value
        )
      }
    )
  }

  private[sbt] def foldMappers[A](mappers: Seq[A => Option[A]]) =
    mappers.foldRight({ p: A =>
      p
    }) {
      (mapper, mappers) =>
        { p: A =>
          mapper(p).getOrElse(mappers(p))
        }
    }
  private[sbt] def none[A]: Option[A] = (None: Option[A])
  private[sbt] def jnone[A]: Optional[A] = none[A].toOptional
  def compileAnalysisSettings: Seq[Setting[_]] = Seq(
    previousCompile := {
      val setup = compileIncSetup.value
      val useBinary: Boolean = enableBinaryCompileAnalysis.value
      val store = MixedAnalyzingCompiler.staticCachedStore(setup.cacheFile.toPath, !useBinary)
      store.get().toOption match {
        case Some(contents) =>
          val analysis = Option(contents.getAnalysis).toOptional
          val setup = Option(contents.getMiniSetup).toOptional
          PreviousResult.of(analysis, setup)
        case None => PreviousResult.of(jnone[CompileAnalysis], jnone[MiniSetup])
      }
    }
  )

  def printWarningsTask: Initialize[Task[Unit]] =
    Def.task {
      val analysis = compile.value match { case a: Analysis => a }
      val max = maxErrors.value
      val spms = sourcePositionMappers.value
      val problems =
        analysis.infos.allInfos.values
          .flatMap(i => i.getReportedProblems ++ i.getUnreportedProblems)
      val reporter = new ManagedLoggedReporter(max, streams.value.log, foldMappers(spms))
      problems.foreach(p => reporter.log(p))
    }

  def sbtPluginExtra(m: ModuleID, sbtV: String, scalaV: String): ModuleID =
    m.extra(
        PomExtraDependencyAttributes.SbtVersionKey -> sbtV,
        PomExtraDependencyAttributes.ScalaVersionKey -> scalaV
      )
      .withCrossVersion(Disabled())

  def discoverSbtPluginNames: Initialize[Task[PluginDiscovery.DiscoveredNames]] = Def.taskDyn {
    if (sbtPlugin.value) Def.task(PluginDiscovery.discoverSourceAll(compile.value))
    else Def.task(PluginDiscovery.emptyDiscoveredNames)
  }

  def copyResourcesTask =
    Def.task {
      val t = classDirectory.value
      val dirs = resourceDirectories.value.toSet
      val s = streams.value
      val cacheStore = s.cacheStoreFactory make "copy-resources"
      val flt: File => Option[File] = flat(t)
      val transform: File => Option[File] = (f: File) => rebase(dirs, t)(f).orElse(flt(f))
      val mappings: Seq[(File, File)] = resources.value.flatMap {
        case r if !dirs(r) => transform(r).map(r -> _)
        case _             => None
      }
      s.log.debug("Copy resource mappings: " + mappings.mkString("\n\t", "\n\t", ""))
      Sync.sync(cacheStore)(mappings)
      mappings
    }

  def runMainParser: (State, Seq[String]) => Parser[(String, Seq[String])] = {
    import DefaultParsers._
    (state, mainClasses) =>
      Space ~> token(NotSpace examples mainClasses.toSet) ~ spaceDelimited("<arg>")
  }

  def testOnlyParser: (State, Seq[String]) => Parser[(Seq[String], Seq[String])] = {
    (state, tests) =>
      import DefaultParsers._
      val selectTests = distinctParser(tests.toSet, true)
      val options = (token(Space) ~> token("--") ~> spaceDelimited("<option>")) ?? Nil
      selectTests ~ options
  }

  private def distinctParser(exs: Set[String], raw: Boolean): Parser[Seq[String]] = {
    import DefaultParsers._
    import Parser.and
    val base = token(Space) ~> token(and(NotSpace, not("--", "Unexpected: ---")) examples exs)
    val recurse = base flatMap { ex =>
      val (matching, notMatching) = exs.partition(GlobFilter(ex).accept _)
      distinctParser(notMatching, raw) map { result =>
        if (raw) ex +: result else matching.toSeq ++ result
      }
    }
    recurse ?? Nil
  }

  val CompletionsID = "completions"

  def noAggregation: Seq[Scoped] =
    Seq(run, runMain, bgRun, bgRunMain, console, consoleQuick, consoleProject)
  lazy val disableAggregation = Defaults.globalDefaults(noAggregation map disableAggregate)
  def disableAggregate(k: Scoped) = aggregate in k :== false

  // 1. runnerSettings is added unscoped via JvmPlugin.
  // 2. In addition it's added scoped to run task.
  lazy val runnerSettings: Seq[Setting[_]] = Seq(runnerTask, forkOptions := forkOptionsTask.value)
  private[this] lazy val newRunnerSettings: Seq[Setting[_]] = {
    val unscoped: Seq[Def.Setting[_]] =
      Seq(runner := ClassLoaders.runner.value, forkOptions := forkOptionsTask.value)
    inConfig(Compile)(unscoped) ++ inConfig(Test)(unscoped)
  }

  lazy val baseTasks: Seq[Setting[_]] = projectTasks ++ packageBase

  lazy val configSettings: Seq[Setting[_]] =
    Classpaths.configSettings ++ configTasks ++ configPaths ++ packageConfig ++
      Classpaths.compilerPluginConfig ++ deprecationSettings ++
      BuildServerProtocol.configSettings

  lazy val compileSettings: Seq[Setting[_]] =
    configSettings ++ (mainBgRunMainTask +: mainBgRunTask) ++ Classpaths.addUnmanagedLibrary

  lazy val testSettings: Seq[Setting[_]] = configSettings ++ testTasks

  lazy val itSettings: Seq[Setting[_]] = inConfig(IntegrationTest) {
    testSettings
  }
  lazy val defaultConfigs: Seq[Setting[_]] = inConfig(Compile)(compileSettings) ++
    inConfig(Test)(testSettings) ++
    inConfig(Runtime)(Classpaths.configSettings)

  // These are project level settings that MUST be on every project.
  lazy val coreDefaultSettings: Seq[Setting[_]] =
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
  // build.sbt is treated a Scala source of metabuild, so to enable deprecation flag on build.sbt we set the option here.
  lazy val deprecationSettings: Seq[Setting[_]] =
    inConfig(Compile)(
      Seq(
        scalacOptions := {
          val old = scalacOptions.value
          val existing = old.toSet
          val d = "-deprecation"
          if (sbtPlugin.value && !existing(d)) d :: old.toList
          else old
        }
      )
    )

  def dependencyResolutionTask: Def.Initialize[Task[DependencyResolution]] =
    Def.taskIf {
      if (useCoursier.value) CoursierDependencyResolution(csrConfiguration.value)
      else IvyDependencyResolution(ivyConfiguration.value, CustomHttp.okhttpClient.value)
    }
}

private[sbt] object Build0 extends BuildExtra

trait BuildExtra extends BuildCommon with DefExtra {
  import Defaults._

  /**
   * Defines an alias given by `name` that expands to `value`.
   * This alias is defined globally after projects are loaded.
   * The alias is undefined when projects are unloaded.
   * Names are restricted to be either alphanumeric or completely symbolic.
   * As an exception, '-' and '_' are allowed within an alphanumeric name.
   */
  def addCommandAlias(name: String, value: String): Seq[Setting[State => State]] = {
    val add = (s: State) => BasicCommands.addAlias(s, name, value)
    val remove = (s: State) => BasicCommands.removeAlias(s, name)
    def compose(setting: SettingKey[State => State], f: State => State) =
      setting in GlobalScope ~= (_ compose f)
    Seq(compose(onLoad, add), compose(onUnload, remove))
  }

  /**
   * Adds Maven resolver plugin.
   */
  def addMavenResolverPlugin: Setting[Seq[ModuleID]] =
    libraryDependencies += sbtPluginExtra(
      ModuleID("org.scala-sbt", "sbt-maven-resolver", sbtVersion.value),
      sbtBinaryVersion.value,
      scalaBinaryVersion.value
    )

  /**
   * Adds `dependency` as an sbt plugin for the specific sbt version `sbtVersion` and Scala version `scalaVersion`.
   * Typically, use the default values for these versions instead of specifying them explicitly.
   */
  def addSbtPlugin(
      dependency: ModuleID,
      sbtVersion: String,
      scalaVersion: String
  ): Setting[Seq[ModuleID]] =
    libraryDependencies += sbtPluginExtra(dependency, sbtVersion, scalaVersion)

  /**
   * Adds `dependency` as an sbt plugin for the specific sbt version `sbtVersion`.
   * Typically, use the default value for this version instead of specifying it explicitly.
   */
  def addSbtPlugin(dependency: ModuleID, sbtVersion: String): Setting[Seq[ModuleID]] =
    libraryDependencies += {
      val scalaV = (scalaBinaryVersion in update).value
      sbtPluginExtra(dependency, sbtVersion, scalaV)
    }

  /**
   * Adds `dependency` as an sbt plugin for the sbt and Scala versions configured by
   * `sbtBinaryVersion` and `scalaBinaryVersion` scoped to `update`.
   */
  def addSbtPlugin(dependency: ModuleID): Setting[Seq[ModuleID]] =
    libraryDependencies += {
      val sbtV = (sbtBinaryVersion in pluginCrossBuild).value
      val scalaV = (scalaBinaryVersion in update).value
      sbtPluginExtra(dependency, sbtV, scalaV)
    }

  /** Transforms `dependency` to be in the auto-compiler plugin configuration. */
  def compilerPlugin(dependency: ModuleID): ModuleID =
    dependency.withConfigurations(Some("plugin->default(compile)"))

  /** Adds `dependency` to `libraryDependencies` in the auto-compiler plugin configuration. */
  def addCompilerPlugin(dependency: ModuleID): Setting[Seq[ModuleID]] =
    libraryDependencies += compilerPlugin(dependency)

  /** Constructs a setting that declares a new artifact `a` that is generated by `taskDef`. */
  def addArtifact(a: Artifact, taskDef: TaskKey[File]): SettingsDefinition = {
    val pkgd = packagedArtifacts := packagedArtifacts.value updated (a, taskDef.value)
    Seq(artifacts += a, pkgd)
  }

  /** Constructs a setting that declares a new artifact `artifact` that is generated by `taskDef`. */
  def addArtifact(
      artifact: Initialize[Artifact],
      taskDef: Initialize[Task[File]]
  ): SettingsDefinition = {
    val artLocal = SettingKey.local[Artifact]
    val taskLocal = TaskKey.local[File]
    val art = artifacts := artLocal.value +: artifacts.value
    val pkgd = packagedArtifacts := packagedArtifacts.value updated (artLocal.value, taskLocal.value)
    Seq(artLocal := artifact.value, taskLocal := taskDef.value, art, pkgd)
  }

  def externalIvySettings(
      file: Initialize[File] = inBase("ivysettings.xml"),
      addMultiResolver: Boolean = true
  ): Setting[Task[IvyConfiguration]] =
    externalIvySettingsURI(file(_.toURI), addMultiResolver)

  def externalIvySettingsURL(
      url: URL,
      addMultiResolver: Boolean = true
  ): Setting[Task[IvyConfiguration]] =
    externalIvySettingsURI(Def.value(url.toURI), addMultiResolver)

  def externalIvySettingsURI(
      uri: Initialize[URI],
      addMultiResolver: Boolean = true
  ): Setting[Task[IvyConfiguration]] = {
    val other = Def.task {
      (
        baseDirectory.value,
        appConfiguration.value,
        projectResolver.value,
        updateOptions.value,
        streams.value
      )
    }
    ivyConfiguration := ((uri zipWith other) {
      case (u, otherTask) =>
        otherTask map {
          case (base, app, pr, uo, s) =>
            val extraResolvers = if (addMultiResolver) Vector(pr) else Vector.empty
            ExternalIvyConfiguration()
              .withLock(lock(app))
              .withBaseDirectory(base)
              .withLog(s.log)
              .withUpdateOptions(uo)
              .withUri(u)
              .withExtraResolvers(extraResolvers)
        }
    }).value
  }

  private[this] def inBase(name: String): Initialize[File] = Def.setting {
    baseDirectory.value / name
  }

  def externalIvyFile(
      file: Initialize[File] = inBase("ivy.xml"),
      iScala: Initialize[Option[ScalaModuleInfo]] = scalaModuleInfo
  ): Setting[Task[ModuleSettings]] =
    moduleSettings := IvyFileConfiguration(
      ivyValidate.value,
      iScala.value,
      file.value,
      managedScalaInstance.value
    )

  def externalPom(
      file: Initialize[File] = inBase("pom.xml"),
      iScala: Initialize[Option[ScalaModuleInfo]] = scalaModuleInfo,
  ): Setting[Task[ModuleSettings]] =
    moduleSettings := PomConfiguration(
      ivyValidate.value,
      iScala.value,
      file.value,
      managedScalaInstance.value,
    )

  def runInputTask(
      config: Configuration,
      mainClass: String,
      baseArguments: String*
  ): Initialize[InputTask[Unit]] =
    Def.inputTask {
      import Def._
      val r = (runner in (config, run)).value
      val cp = (fullClasspath in config).value
      val args = spaceDelimited().parsed
      r.run(mainClass, data(cp), baseArguments ++ args, streams.value.log).get
    }

  def runTask(
      config: Configuration,
      mainClass: String,
      arguments: String*
  ): Initialize[Task[Unit]] =
    Def.task {
      val cp = (fullClasspath in config).value
      val r = (runner in (config, run)).value
      val s = streams.value
      r.run(mainClass, data(cp), arguments, s.log).get
    }

  // public API
  /** Returns a vector of settings that create custom run input task. */
  def fullRunInputTask(
      scoped: InputKey[Unit],
      config: Configuration,
      mainClass: String,
      baseArguments: String*
  ): Vector[Setting[_]] = {
    // TODO: Re-write to avoid InputTask.apply which is deprecated
    // I tried "Def.spaceDelimited().parsed" (after importing Def.parserToInput)
    // but it broke actions/run-task
    // Maybe it needs to be defined inside a Def.inputTask?
    @com.github.ghik.silencer.silent
    def inputTask[T](f: TaskKey[Seq[String]] => Initialize[Task[T]]): Initialize[InputTask[T]] =
      InputTask.apply(Def.value((s: State) => Def.spaceDelimited()))(f)

    Vector(
      scoped := inputTask { result =>
        initScoped(
          scoped.scopedKey,
          ClassLoaders.runner mapReferenced Project.mapScope(s => s.in(config))
        ).zipWith(Def.task { ((fullClasspath in config).value, streams.value, result.value) }) {
          (rTask, t) =>
            (t, rTask) map {
              case ((cp, s, args), r) =>
                r.run(mainClass, data(cp), baseArguments ++ args, s.log).get
            }
        }
      }.evaluated
    ) ++ inTask(scoped)(forkOptions in config := forkOptionsTask.value)
  }

  // public API
  /** Returns a vector of settings that create custom run task. */
  def fullRunTask(
      scoped: TaskKey[Unit],
      config: Configuration,
      mainClass: String,
      arguments: String*
  ): Vector[Setting[_]] =
    Vector(
      scoped := initScoped(
        scoped.scopedKey,
        ClassLoaders.runner mapReferenced Project.mapScope(s => s.in(config))
      ).zipWith(Def.task { ((fullClasspath in config).value, streams.value) }) {
          case (rTask, t) =>
            (t, rTask) map {
              case ((cp, s), r) =>
                r.run(mainClass, data(cp), arguments, s.log).get
            }
        }
        .value
    ) ++ inTask(scoped)(forkOptions in config := forkOptionsTask.value)

  def initScoped[T](sk: ScopedKey[_], i: Initialize[T]): Initialize[T] =
    initScope(fillTaskAxis(sk.scope, sk.key), i)
  def initScope[T](s: Scope, i: Initialize[T]): Initialize[T] =
    i mapReferenced Project.mapScope(Scope.replaceThis(s))

  /**
   * Disables post-compilation hook for determining tests for tab-completion (such as for 'test-only').
   * This is useful for reducing test:compile time when not running test.
   */
  def noTestCompletion(config: Configuration = Test): Setting[_] =
    inConfig(config)(Seq(definedTests := detectTests.value)).head

  def filterKeys(ss: Seq[Setting[_]], transitive: Boolean = false)(
      f: ScopedKey[_] => Boolean
  ): Seq[Setting[_]] =
    ss filter (s => f(s.key) && (!transitive || s.dependencies.forall(f)))
}

trait DefExtra {
  private[this] val ts: TaskSequential = new TaskSequential {}
  implicit def toTaskSequential(@deprecated("unused", "") d: Def.type): TaskSequential = ts
}

trait BuildCommon {

  /**
   * Allows a String to be used where a `NameFilter` is expected.
   * Asterisks (`*`) in the string are interpreted as wildcards.
   * All other characters must match exactly.  See GlobFilter.
   */
  implicit def globFilter(expression: String): NameFilter = GlobFilter(expression)

  implicit def richAttributed(s: Seq[Attributed[File]]): RichAttributed = new RichAttributed(s)
  implicit def richFiles(s: Seq[File]): RichFiles = new RichFiles(s)
  implicit def richPathFinder(s: PathFinder): RichPathFinder = new RichPathFinder(s)
  final class RichPathFinder private[sbt] (s: PathFinder) {

    /** Converts the `PathFinder` to a `Classpath`, which is an alias for `Seq[Attributed[File]]`. */
    def classpath: Classpath = Attributed blankSeq s.get
  }
  final class RichAttributed private[sbt] (s: Seq[Attributed[File]]) {

    /** Extracts the plain `Seq[File]` from a Classpath (which is a `Seq[Attributed[File]]`).*/
    def files: Seq[File] = Attributed.data(s)
  }
  final class RichFiles private[sbt] (s: Seq[File]) {

    /** Converts the `Seq[File]` to a Classpath, which is an alias for `Seq[Attributed[File]]`. */
    def classpath: Classpath = Attributed blankSeq s
  }

  def overrideConfigs(cs: Configuration*)(
      configurations: Seq[Configuration]
  ): Seq[Configuration] = {
    val existingName = configurations.map(_.name).toSet
    val newByName = cs.map(c => (c.name, c)).toMap
    val overridden = configurations map { conf =>
      newByName.getOrElse(conf.name, conf)
    }
    val newConfigs = cs filter { c =>
      !existingName(c.name)
    }
    overridden ++ newConfigs
  }

  // these are intended for use in in put tasks for creating parsers
  def getFromContext[T](task: TaskKey[T], context: ScopedKey[_], s: State): Option[T] =
    SessionVar.get(SessionVar.resolveContext(task.scopedKey, context.scope, s), s)

  def loadFromContext[T](task: TaskKey[T], context: ScopedKey[_], s: State)(
      implicit f: JsonFormat[T]
  ): Option[T] =
    SessionVar.load(SessionVar.resolveContext(task.scopedKey, context.scope, s), s)

  // intended for use in constructing InputTasks
  def loadForParser[P, T](task: TaskKey[T])(
      f: (State, Option[T]) => Parser[P]
  )(implicit format: JsonFormat[T]): Initialize[State => Parser[P]] =
    loadForParserI(task)(Def value f)(format)
  def loadForParserI[P, T](task: TaskKey[T])(
      init: Initialize[(State, Option[T]) => Parser[P]]
  )(implicit format: JsonFormat[T]): Initialize[State => Parser[P]] =
    Def.setting { (s: State) =>
      init.value(s, loadFromContext(task, resolvedScoped.value, s)(format))
    }

  def getForParser[P, T](
      task: TaskKey[T]
  )(init: (State, Option[T]) => Parser[P]): Initialize[State => Parser[P]] =
    getForParserI(task)(Def value init)
  def getForParserI[P, T](
      task: TaskKey[T]
  )(init: Initialize[(State, Option[T]) => Parser[P]]): Initialize[State => Parser[P]] =
    Def.setting { (s: State) =>
      init.value(s, getFromContext(task, resolvedScoped.value, s))
    }

  // these are for use for constructing Tasks
  def loadPrevious[T](task: TaskKey[T])(implicit f: JsonFormat[T]): Initialize[Task[Option[T]]] =
    Def.task { loadFromContext(task, resolvedScoped.value, state.value)(f) }
  def getPrevious[T](task: TaskKey[T]): Initialize[Task[Option[T]]] =
    Def.task { getFromContext(task, resolvedScoped.value, state.value) }

  private[sbt] def derive[T](s: Setting[T]): Setting[T] =
    Def.derive(s, allowDynamic = true, trigger = _ != streams.key, default = true)
}
