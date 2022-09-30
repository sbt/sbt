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
import lmcoursier.definitions.{ Configuration => CConfiguration }
import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.logging.log4j.core.{ Appender => XAppender }
import org.scalasbt.ipcsocket.Win32SecurityLevel
import sbt.Def.{ Initialize, ScopedKey, Setting, SettingsDefinition }
import sbt.Keys._
import sbt.OptionSyntax._
import sbt.Project.{
  inConfig,
  inScope,
  inTask,
  richInitialize,
  richInitializeTask,
  richTaskSessionVar,
  sbtRichTaskPromise
}
import sbt.Scope.{ GlobalScope, ThisScope, fillTaskAxis }
import sbt.coursierint._
import sbt.internal.CommandStrings.ExportStream
import sbt.internal._
import sbt.internal.classpath.AlternativeZincUtil
import sbt.internal.inc.JavaInterfaceUtil._
import sbt.internal.inc.classpath.{ ClasspathFilter, ClasspathUtil }
import sbt.internal.inc.{ CompileOutput, MappedFileConverter, Stamps, ZincLmUtil, ZincUtil }
import sbt.internal.io.{ Source, WatchState }
import sbt.internal.librarymanagement.mavenint.{
  PomExtraDependencyAttributes,
  SbtPomExtraProperties
}
import sbt.internal.librarymanagement._
import sbt.internal.nio.{ CheckBuildSources, Globs }
import sbt.internal.server.{
  BspCompileProgress,
  BspCompileTask,
  BuildServerProtocol,
  BuildServerReporter,
  Definition,
  LanguageServerProtocol,
  ServerHandler,
  VirtualTerminal
}
import sbt.internal.testing.TestLogger
import sbt.internal.util.Attributed.data
import sbt.internal.util.Types._
import sbt.internal.util.{ Terminal => ITerminal, _ }
import sbt.internal.util.complete._
import sbt.io.Path._
import sbt.io._
import sbt.io.syntax._
import sbt.librarymanagement.Artifact.{ DocClassifier, SourceClassifier }
import sbt.librarymanagement.Configurations.{
  Compile,
  CompilerPlugin,
  IntegrationTest,
  Provided,
  Runtime,
  Test
}
import sbt.librarymanagement.CrossVersion.{ binarySbtVersion, binaryScalaVersion, partialVersion }
import sbt.librarymanagement._
import sbt.librarymanagement.ivy._
import sbt.librarymanagement.syntax._
import sbt.nio.FileStamp
import sbt.nio.Keys._
import sbt.nio.file.syntax._
import sbt.nio.file.{ FileTreeView, Glob, RecursiveGlob }
import sbt.nio.Watch
import sbt.std.TaskExtra._
import sbt.testing.{ AnnotatedFingerprint, Framework, Runner, SubclassFingerprint }
import sbt.util.CacheImplicits._
import sbt.util.InterfaceUtil.{ t2, toJavaFunction => f1 }
import sbt.util._
import sjsonnew._
import sjsonnew.support.scalajson.unsafe.Converter
import xsbti.compile.TastyFiles
import xsbti.{ FileConverter, Position }

import scala.annotation.nowarn
import scala.collection.immutable.ListMap
import scala.concurrent.duration._
import scala.util.control.NonFatal
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
      (ThisScope.copy(config = Zero) / key).value / nameForSrc(configuration.value.name)
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
      onChangedBuildSource :== SysProp.onChangedBuildSource,
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
      reportAbsolutePath := true,
      run / traceLevel :== 0,
      runMain / traceLevel :== 0,
      bgRun / traceLevel :== 0,
      fgRun / traceLevel :== 0,
      console / traceLevel :== Int.MaxValue,
      consoleProject / traceLevel :== Int.MaxValue,
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
      packageSrc / artifactClassifier :== Some(SourceClassifier),
      packageDoc / artifactClassifier :== Some(DocClassifier),
      includeFilter :== NothingFilter,
      unmanagedSources / includeFilter :== ("*.java" | "*.scala"),
      unmanagedJars / includeFilter :== "*.jar" | "*.so" | "*.dll" | "*.jnilib" | "*.zip",
      unmanagedResources / includeFilter :== AllPassFilter,
      bgList := { bgJobService.value.jobs },
      ps := psTask.value,
      bgStop := bgStopTask.evaluated,
      bgWaitFor := bgWaitForTask.evaluated,
      bgCopyClasspath :== true,
      closeClassLoaders :== SysProp.closeClassLoaders,
      allowZombieClassLoaders :== true,
      packageTimestamp :== Package.defaultTimestamp,
    ) ++ BuildServerProtocol.globalSettings

  private[sbt] lazy val globalIvyCore: Seq[Setting[_]] =
    Seq(
      internalConfigurationMap :== Configurations.internalMap _,
      credentials :== SysProp.sbtCredentialsEnv.toList,
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
      checkBuildSources / aggregate :== false,
      checkBuildSources / changedInputFiles / aggregate := false,
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
      pluginCrossBuild / sbtVersion := sbtVersion.value,
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
      useSuperShell := { if (insideCI.value) false else ITerminal.console.isSupershellEnabled },
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
      serverUseJni := BootServerSocket.requiresJNI || SysProp.serverUseJni,
      fullServerHandlers := Nil,
      insideCI :== sys.env.contains("BUILD_NUMBER") ||
        sys.env.contains("CI") || SysProp.ci,
      // watch related settings
      pollInterval :== Watch.defaultPollInterval,
      canonicalInput :== true,
      echoInput :== true,
      terminal := state.value.get(terminalKey).getOrElse(Terminal(ITerminal.get)),
      InstallSbtn.installSbtn := InstallSbtn.installSbtnImpl.evaluated,
      InstallSbtn.installSbtn / aggregate := false,
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
    sourcePositionMappers := Nil, // Never set a default sourcePositionMapper, see #6352! Whatever you are trying to solve, do it in the foldMappers method.
    // The virtual file value cache needs to be global or sbt will run out of direct byte buffer memory.
    classpathDefinesClassCache := VirtualFileValueCache.definesClassCache(fileConverter.value),
    fullServerHandlers := {
      Seq(
        LanguageServerProtocol.handler(fileConverter.value),
        BuildServerProtocol.handler(
          loadedBuild.value,
          bspFullWorkspace.value,
          sbtVersion.value,
          semanticdbEnabled.value,
          semanticdbVersion.value
        ),
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

  private[sbt] def toAbsoluteSource(fc: FileConverter)(pos: Position): Position = {
    val newPath: Option[NioPath] = pos
      .sourcePath()
      .asScala
      .flatMap { path =>
        try {
          Some(fc.toPath(VirtualFileRef.of(path)))
        } catch {
          // catch all to trap wierd path injected by compiler, users, or plugins
          case NonFatal(_) => None
        }
      }

    newPath
      .map { path =>
        new Position {
          override def line(): Optional[Integer] = pos.line()

          override def lineContent(): String = pos.lineContent()

          override def offset(): Optional[Integer] = pos.offset()

          override def pointer(): Optional[Integer] = pos.pointer()

          override def pointerSpace(): Optional[String] = pos.pointerSpace()

          override def sourcePath(): Optional[String] = Optional.of(path.toAbsolutePath.toString)

          override def sourceFile(): Optional[File] =
            (try {
              Some(path.toFile.getAbsoluteFile)
            } catch {
              case NonFatal(_) => None
            }).toOptional

          override def startOffset(): Optional[Integer] = pos.startOffset()

          override def endOffset(): Optional[Integer] = pos.endOffset()

          override def startLine(): Optional[Integer] = pos.startLine()

          override def startColumn(): Optional[Integer] = pos.startColumn()

          override def endLine(): Optional[Integer] = pos.endLine()

          override def endColumn(): Optional[Integer] = pos.endColumn()
        }
      }
      .getOrElse(pos)
  }

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
      val isDotty = ScalaInstance.isDotty(scalaVersion.value)
      val epochVersion = if (isDotty) "3" else "2"
      makeCrossSources(
        scalaSource.value,
        javaSource.value,
        scalaBinaryVersion.value,
        epochVersion,
        crossPaths.value
      ) ++
        makePluginCrossSources(
          sbtPlugin.value,
          scalaSource.value,
          (pluginCrossBuild / sbtBinaryVersion).value,
          crossPaths.value
        )
    },
    unmanagedSources / fileInputs := {
      val include = (unmanagedSources / includeFilter).value
      val filter = (unmanagedSources / excludeFilter).value match {
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
      val include = (unmanagedResources / includeFilter).value
      val filter = (unmanagedResources / excludeFilter).value match {
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
    earlyOutput / artifactPath := configArtifactPathSetting(artifact, "early").value,
    earlyOutput := {
      val converter = fileConverter.value
      val jar = (earlyOutput / artifactPath).value
      converter.toVirtualFile(jar.toPath)
    },
    semanticdbTargetRoot := crossTarget.value / (prefix(configuration.value.name) + "meta"),
    compileAnalysisTargetRoot := crossTarget.value / (prefix(configuration.value.name) + "zinc"),
    earlyCompileAnalysisTargetRoot := crossTarget.value / (prefix(configuration.value.name) + "early-zinc"),
    doc / target := crossTarget.value / (prefix(configuration.value.name) + "api")
  )

  // This is included into JvmPlugin.projectSettings
  def compileBase = inTask(console)(compilersSetting :: Nil) ++ compileBaseGlobal ++ Seq(
    useScalaReplJLine :== false,
    scalaInstanceTopLoader := {
      val topLoader = if (!useScalaReplJLine.value) {
        // the JLineLoader contains the SbtInterfaceClassLoader
        classOf[org.jline.terminal.Terminal].getClassLoader
      } else classOf[Compilers].getClassLoader // the SbtInterfaceClassLoader

      // Scala 2.10 shades jline in the console so we need to make sure that it loads a compatible
      // jansi version. Because of the shading, console does not work with the thin client for 2.10.x.
      if (scalaVersion.value.startsWith("2.10.")) new ClassLoader(topLoader) {
        override protected def loadClass(name: String, resolve: Boolean): Class[_] = {
          if (name.startsWith("org.fusesource")) throw new ClassNotFoundException(name)
          super.loadClass(name, resolve)
        }
      }
      else topLoader
    },
    scalaInstance := scalaInstanceTask.value,
    crossVersion := (if (crossPaths.value) CrossVersion.binary else CrossVersion.disabled),
    pluginCrossBuild / sbtBinaryVersion := binarySbtVersion(
      (pluginCrossBuild / sbtVersion).value
    ),
    // Use (sbtVersion in pluginCrossBuild) to pick the sbt module to depend from the plugin.
    // Because `sbtVersion in pluginCrossBuild` can be scoped to project level,
    // this setting needs to be set here too.
    pluginCrossBuild / sbtDependency := {
      val app = appConfiguration.value
      val id = app.provider.id
      val sv = (pluginCrossBuild / sbtVersion).value
      val scalaV = (pluginCrossBuild / scalaVersion).value
      val binVersion = (pluginCrossBuild / scalaBinaryVersion).value
      val cross = id.crossVersionedValue match {
        case CrossValue.Disabled => Disabled()
        case CrossValue.Full     => CrossVersion.full
        case CrossValue.Binary   => CrossVersion.binary
      }
      val base = ModuleID(id.groupID, id.name, sv).withCrossVersion(cross)
      CrossVersion(scalaV, binVersion)(base).withCrossVersion(Disabled())
    },
    crossSbtVersions := Vector((pluginCrossBuild / sbtVersion).value),
    crossTarget := makeCrossTarget(
      target.value,
      scalaVersion.value,
      scalaBinaryVersion.value,
      (pluginCrossBuild / sbtBinaryVersion).value,
      sbtPlugin.value,
      crossPaths.value
    ),
    cleanIvy := IvyActions.cleanCachedResolutionCache(ivyModule.value, streams.value.log),
    clean := clean.dependsOn(cleanIvy).value,
    scalaCompilerBridgeBinaryJar := Def.settingDyn {
      val sv = scalaVersion.value
      if (ScalaArtifacts.isScala3(sv)) fetchBridgeBinaryJarTask(sv)
      else Def.task[Option[File]](None)
    }.value,
    scalaCompilerBridgeSource := ZincLmUtil.getDefaultBridgeSourceModule(scalaVersion.value),
    auxiliaryClassFiles ++= {
      if (ScalaArtifacts.isScala3(scalaVersion.value)) List(TastyFiles.instance)
      else Nil
    },
    consoleProject / scalaCompilerBridgeBinaryJar := None,
    consoleProject / scalaCompilerBridgeSource := ZincLmUtil.getDefaultBridgeSourceModule(
      appConfiguration.value.provider.scalaProvider.version
    ),
  )
  // must be a val: duplication detected by object identity
  private[this] lazy val compileBaseGlobal: Seq[Setting[_]] = globalDefaults(
    Seq(
      auxiliaryClassFiles :== Nil,
      incOptions := IncOptions.of(),
      classpathOptions :== ClasspathOptionsUtil.boot,
      console / classpathOptions :== ClasspathOptionsUtil.repl,
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
      epochVersion: String,
      cross: Boolean
  ): Seq[File] = {
    if (cross)
      Seq(
        scalaSrcDir,
        scalaSrcDir.getParentFile / s"${scalaSrcDir.name}-$sv",
        scalaSrcDir.getParentFile / s"${scalaSrcDir.name}-$epochVersion",
        javaSrcDir,
      ).distinct
    else
      Seq(scalaSrcDir, javaSrcDir)
  }

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

  @deprecated("Use constructor with scalaVersion and scalaBinaryVersion", "1.5.0")
  def makeCrossTarget(t: File, bv: String, sbtv: String, plugin: Boolean, cross: Boolean): File = {
    val scalaBase = if (cross) t / ("scala-" + bv) else t
    if (plugin) scalaBase / ("sbt-" + sbtv) else scalaBase
  }

  def makeCrossTarget(
      t: File,
      scalaVersion: String,
      scalaBinaryVersion: String,
      sbtv: String,
      plugin: Boolean,
      cross: Boolean
  ): File = {
    val scalaSuffix =
      if (ScalaArtifacts.isScala3(scalaVersion)) scalaVersion else scalaBinaryVersion
    val scalaBase = if (cross) t / ("scala-" + scalaSuffix) else t
    if (plugin) scalaBase / ("sbt-" + sbtv) else scalaBase
  }

  private def fetchBridgeBinaryJarTask(scalaVersion: String): Initialize[Task[Option[File]]] =
    Def.task {
      val bridgeJar = ZincLmUtil.fetchDefaultBridgeModule(
        scalaVersion,
        dependencyResolution.value,
        updateConfiguration.value,
        (update / unresolvedWarningConfiguration).value,
        streams.value.log
      )
      Some(bridgeJar)
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
    inTask(compileJava)(
      Seq(
        compileInputs := {
          val opts = (compileJava / compileOptions).value
          (compile / compileInputs).value.withOptions(opts)
        },
        compileOptions := {
          val opts = (compile / compileOptions).value
          val cp0 = dependencyVirtualClasspath.value
          val cp = backendOutput.value +: data(cp0)
          opts.withClasspath(cp.toArray)
        }
      )
    ) ++
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
    tastyFiles := Def.taskIf {
      if (ScalaArtifacts.isScala3(scalaVersion.value)) {
        val _ = compile.value
        val tastyFiles = classDirectory.value.**("*.tasty").get
        tastyFiles.map(_.getAbsoluteFile)
      } else Nil
    }.value,
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
        .withAuxiliaryClassFiles(auxiliaryClassFiles.value.toArray)
        .withExternalHooks(externalHooks.value)
        .withClassfileManagerType(
          Option(
            TransactionalManagerType
              .of( // https://github.com/sbt/sbt/issues/1673
                crossTarget.value / s"${prefix(configuration.value.name)}classes.bak",
                streams.value.log
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
    scalacOptions := {
      val old = scalacOptions.value
      if (sbtPlugin.value && VersionNumber(scalaVersion.value)
            .matchesSemVer(SemanticSelector("=2.12 >=2.12.13")))
        old ++ Seq("-Wconf:cat=unused-nowarn:s", "-Xsource:3")
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
    run / mainClass := (run / selectMainClass).value,
    mainClass := {
      val logWarning = state.value.currentCommand.forall(!_.commandLine.split(" ").exists {
        case "run" | "runMain" => true
        case r =>
          r.split("/") match {
            case Array(parts @ _*) =>
              parts.lastOption match {
                case Some("run" | "runMain") => true
                case _                       => false
              }
          }
      })
      pickMainClassOrWarn(discoveredMainClasses.value, streams.value.log, logWarning)
    },
    runMain := foregroundRunMainTask.evaluated,
    run := foregroundRunTask.evaluated,
    fgRun := runTask(fullClasspath, (run / mainClass), (run / runner)).evaluated,
    fgRunMain := runMainTask(fullClasspath, (run / runner)).evaluated,
    copyResources := copyResourcesTask.value,
    // note that we use the same runner and mainClass as plain run
    mainBgRunMainTaskForConfig(This),
    mainBgRunTaskForConfig(This)
  ) ++ inTask(run)(runnerSettings ++ newRunnerSettings)

  private[this] lazy val configGlobal = globalDefaults(
    Seq(
      initialCommands :== "",
      cleanupCommands :== "",
      asciiGraphWidth :== 80
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
        val scoped = (base / watchTransitiveSources)
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
                makeScalaInstance(
                  version,
                  libraryJars,
                  allJars,
                  Seq.empty,
                  state.value,
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
    val sv = scalaVersion.value
    val fullReport = update.value

    val toolReport = fullReport
      .configuration(Configurations.ScalaTool)
      .getOrElse(sys.error(noToolConfiguration(managedScalaInstance.value)))

    def file(id: String): File = {
      val files = for {
        m <- toolReport.modules if m.module.name.startsWith(id)
        (art, file) <- m.artifacts if art.`type` == Artifact.DefaultType
      } yield file
      files.headOption getOrElse sys.error(s"Missing $id jar file")
    }

    val allCompilerJars = toolReport.modules.flatMap(_.artifacts.map(_._2))
    val allDocJars =
      fullReport
        .configuration(Configurations.ScalaDocTool)
        .toSeq
        .flatMap(_.modules)
        .flatMap(_.artifacts.map(_._2))
    val libraryJars = ScalaArtifacts.libraryIds(sv).map(file)

    makeScalaInstance(
      sv,
      libraryJars,
      allCompilerJars,
      allDocJars,
      state.value,
      scalaInstanceTopLoader.value,
    )
  }
  def makeScalaInstance(
      version: String,
      libraryJars: Array[File],
      allCompilerJars: Seq[File],
      allDocJars: Seq[File],
      state: State,
      topLoader: ClassLoader,
  ): ScalaInstance = {
    val classLoaderCache = state.extendedClassLoaderCache
    val compilerJars = allCompilerJars.filterNot(libraryJars.contains).distinct.toArray
    val docJars = allDocJars
      .filterNot(jar => libraryJars.contains(jar) || compilerJars.contains(jar))
      .distinct
      .toArray
    val allJars = libraryJars ++ compilerJars ++ docJars

    val libraryLoader = classLoaderCache(libraryJars.toList, topLoader)
    val compilerLoader = classLoaderCache(compilerJars.toList, libraryLoader)
    val fullLoader =
      if (docJars.isEmpty) compilerLoader
      else classLoaderCache(docJars.distinct.toList, compilerLoader)
    new ScalaInstance(
      version = version,
      loader = fullLoader,
      loaderCompilerOnly = compilerLoader,
      loaderLibraryOnly = libraryLoader,
      libraryJars = libraryJars,
      compilerJars = compilerJars,
      allJars = allJars,
      explicitActual = Some(version)
    )
  }
  def scalaInstanceFromHome(dir: File): Initialize[Task[ScalaInstance]] = Def.task {
    val dummy = ScalaInstance(dir)(state.value.classLoaderCache.apply)
    Seq(dummy.loader, dummy.loaderLibraryOnly).foreach {
      case a: AutoCloseable => a.close()
      case _                =>
    }
    makeScalaInstance(
      dummy.version,
      dummy.libraryJars,
      dummy.compilerJars,
      dummy.allJars,
      state.value,
      scalaInstanceTopLoader.value,
    )
  }

  private[this] def testDefaults =
    Defaults.globalDefaults(
      Seq(
        testFrameworks :== {
          import sbt.TestFrameworks._
          Seq(ScalaCheck, Specs2, Specs, ScalaTest, JUnit, MUnit)
        },
        testListeners :== Nil,
        testOptions :== Nil,
        testResultLogger :== TestResultLogger.Default,
        testOnly / testFilter :== (selectedFilter _)
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
    testQuick / testFilter := testQuickFilter.value,
    executeTests := (
      Def.taskDyn {
        allTestGroupsTask(
          (test / streams).value,
          loadedTestFrameworks.value,
          testLoader.value,
          (test / testGrouping).value,
          (test / testExecution).value,
          (test / fullClasspath).value,
          testForkedParallel.value,
          (test / javaOptions).value,
          (classLoaderLayeringStrategy).value,
          projectId = s"${thisProject.value.id} / ",
        )
      }
    ).value,
    // ((streams in test, loadedTestFrameworks, testLoader, testGrouping in test, testExecution in test, fullClasspath in test, javaHome in test, testForkedParallel, javaOptions in test) flatMap allTestGroupsTask).value,
    Test / test / testResultLogger :== TestResultLogger.SilentWhenNoTests, // https://github.com/sbt/sbt/issues/1185
    test := {
      val trl = (Test / test / testResultLogger).value
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
              (resolvedScoped.value.scope / test),
              logBuffered.value
            ),
            Keys.logLevel.?.value.getOrElse(stateLogLevel),
          ) +:
            new TestStatusReporter(succeededFile((test / streams).value.cacheDirectory)) +:
            (TaskZero / testListeners).value
        },
        testOptions := Tests.Listeners(testListeners.value) +: (TaskZero / testOptions).value,
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
        (task / testOptions).value,
        (task / parallelExecution).value,
        (task / tags).value
      )
    }

  def testQuickFilter: Initialize[Task[Seq[String] => Seq[String => Boolean]]] =
    Def.task {
      val cp = (test / fullClasspath).value
      val s = (test / streams).value
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
                  rel.externalDeps(c).map(stamp) ++
                  rel.productClassName.reverse(c).flatMap { pc =>
                    apis.internal.get(pc).map(_.compilationTimestamp)
                  } + Long.MinValue
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
        case Some(ts) => stamps.synchronized(stamp(test)) > ts
      }
      args =>
        for (filter <- selectedFilter(args))
          yield (test: String) => filter(test) && noSuccessYet(test)
    }
  def succeededFile(dir: File) = dir / "succeeded_tests"

  @nowarn
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
    val processedOptions: Map[Tests.Group, Tests.ProcessedOptions] =
      groups
        .map(
          group => group -> Tests.processOptions(config, group.tests.toVector, s.log)
        )
        .toMap

    val testDefinitions: Iterable[TestDefinition] = processedOptions.values.flatMap(_.tests)

    val filteredFrameworks: Map[TestFramework, Framework] = frameworks.filter {
      case (_, framework) =>
        TestFramework.getFingerprints(framework).exists { t =>
          testDefinitions.exists { test =>
            TestFramework.matches(t, test.fingerprint)
          }
        }
    }

    val runners = createTestRunners(filteredFrameworks, loader, config)

    val groupTasks = groups map { group =>
      group.runPolicy match {
        case Tests.SubProcess(opts) =>
          s.log.debug(s"javaOptions: ${opts.runJVMOptions}")
          val forkedConfig = config.copy(parallel = config.parallel && forkedParallelExecution)
          s.log.debug(s"Forking tests - parallelism = ${forkedConfig.parallel}")
          ForkTests(
            runners,
            processedOptions(group),
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
            processedOptions(group),
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
          val ts = packageTimestamp.value
          val old = packageOptions.value

          Package.addSpecManifestAttributes(n, ver, orgName) +:
            Package.addImplManifestAttributes(n, ver, homepage.value, org, orgName) +:
            Package.setFixedTimestamp(ts) +:
            main.map(Package.MainClass.apply) ++: old
        }
      )
    ) ++
      inTask(packageSrc)(
        Seq(
          packageOptions := {
            val old = packageOptions.value
            val ts = packageTimestamp.value
            Package.addSpecManifestAttributes(
              name.value,
              version.value,
              organizationName.value
            ) +: Package.setFixedTimestamp(ts) +: old
          }
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

  private[sbt] def configArtifactPathSetting(
      art: SettingKey[Artifact],
      extraPrefix: String
  ): Initialize[File] =
    Def.setting {
      val f = artifactName.value
      crossTarget.value /
        (prefix(configuration.value.name) + extraPrefix) / f(
        ScalaVersion(
          (artifactName / scalaVersion).value,
          (artifactName / scalaBinaryVersion).value
        ),
        projectID.value,
        art.value
      )
    }

  private[sbt] def prefixArtifactPathSetting(
      art: SettingKey[Artifact],
      extraPrefix: String
  ): Initialize[File] =
    Def.setting {
      val f = artifactName.value
      crossTarget.value / extraPrefix / f(
        ScalaVersion(
          (artifactName / scalaVersion).value,
          (artifactName / scalaBinaryVersion).value
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
          (artifactName / scalaVersion).value,
          (artifactName / scalaBinaryVersion).value
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
        (TaskZero / key) := packageTask.value,
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
        Package.timeFromConfiguration(config)
      )
      config.jar
    }

  def packageConfigurationTask: Initialize[Task[Package.Configuration]] =
    Def.task {
      new Package.Configuration(mappings.value, artifactPath.value, packageOptions.value)
    }

  def askForMainClass(classes: Seq[String]): Option[String] =
    sbt.SelectMainClass(
      if (classes.length >= 10) Some(SimpleReader(ITerminal.get).readLine(_))
      else
        Some(s => {
          def print(st: String) = { scala.Console.out.print(st); scala.Console.out.flush() }
          print(s)
          ITerminal.get.withRawInput {
            try ITerminal.get.inputStream.read match {
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

  private[this] def termWrapper(canonical: Boolean, echo: Boolean): (() => Unit) => (() => Unit) =
    (f: () => Unit) =>
      () => {
        val term = ITerminal.get
        if (!canonical) {
          term.enterRawMode()
          if (echo) term.setEchoEnabled(echo)
        } else if (!echo) term.setEchoEnabled(false)
        try f()
        finally {
          if (!canonical) term.exitRawMode()
          if (!echo) term.setEchoEnabled(true)
        }
      }
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
      val hashClasspath = (bgRunMain / bgHashClasspath).value
      val wrapper = termWrapper(canonicalInput.value, echoInput.value)
      service.runInBackgroundWithLoader(resolvedScoped.value, state.value) { (logger, workingDir) =>
        val files =
          if (copyClasspath.value)
            service.copyClasspath(products.value, classpath.value, workingDir, hashClasspath)
          else classpath.value
        val cp = data(files)
        scalaRun.value match {
          case r: Run =>
            val loader = r.newLoader(cp)
            (Some(loader), wrapper(() => r.runWithLoader(loader, cp, mainClass, args, logger).get))
          case sr =>
            (None, wrapper(() => sr.run(mainClass, cp, args, logger).get))
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
      val hashClasspath = (bgRun / bgHashClasspath).value
      val wrapper = termWrapper(canonicalInput.value, echoInput.value)
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
            (Some(loader), wrapper(() => r.runWithLoader(loader, cp, mainClass, args, logger).get))
          case sr =>
            (None, wrapper(() => sr.run(mainClass, cp, args, logger).get))
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
          (resolvedScope / javaOptions).scopedKey.scope,
          (resolvedScope / javaOptions).key.label,
          mask
        )
        val showFork = Scope.displayMasked(
          (resolvedScope / fork).scopedKey.scope,
          (resolvedScope / fork).key.label,
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
        scalacOptions := {
          val compileOptions = scalacOptions.value
          val sv = scalaVersion.value
          val config = configuration.value
          val projectName = name.value
          if (ScalaArtifacts.isScala3(sv)) {
            val project = if (config == Compile) projectName else s"$projectName-$config"
            if (scalaVersion.value.startsWith("3.0.0")) {
              Seq("-project", project)
            } else {
              compileOptions ++ Seq("-project", project)
            }
          } else compileOptions
        },
        (TaskZero / key) := {
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
          val reporter = (compile / bspReporter).value
          val converter = fileConverter.value
          val tFiles = tastyFiles.value
          val sv = scalaVersion.value
          val allDeps = allDependencies.value
          (hasScala, hasJava) match {
            case (true, _) =>
              val options = sOpts ++ Opts.doc.externalAPI(xapis)
              val runDoc = Doc.scaladoc(label, s.cacheStoreFactory sub "scala", cs.scalac match {
                case ac: AnalyzingCompiler => ac.onArgs(exported(s, "scaladoc"))
              }, fiOpts)
              def isScala3Doc(module: ModuleID): Boolean = {
                module.configurations.exists(_.startsWith(Configurations.ScalaDocTool.name)) &&
                module.name == ScalaArtifacts.Scala3DocID
              }
              if (ScalaArtifacts.isScala3M123(sv) && !allDeps.exists(isScala3Doc)) {
                Array(
                  "Unresolved scala3doc artifact",
                  "add 'ThisBuild / resolvers += Resolver.JCenterRepository'"
                ).foreach(m => s.log.error(m))
              }
              val docSrcs = if (ScalaArtifacts.isScala3(sv)) tFiles else srcs
              runDoc(docSrcs, cp, out, options, maxErrors.value, s.log)
            case (_, true) =>
              val javadoc =
                sbt.inc.Doc.cachedJavadoc(label, s.cacheStoreFactory sub "java", cs.javaTools)
              javadoc.run(
                srcs.toList map { x =>
                  converter.toVirtualFile(x.toPath)
                },
                cp map { x =>
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
      This / c / This / fullClasspathAsJars,
      run / mainClass,
      bgRun / bgCopyClasspath,
      run / runner
    ).evaluated

  private[this] def mainBgRunMainTaskForConfig(c: ScopeAxis[ConfigKey]) =
    bgRunMain := bgRunMainTask(
      exportedProductJars,
      This / c / This / fullClasspathAsJars,
      bgRunMain / bgCopyClasspath,
      run / runner
    ).evaluated

  def discoverMainClasses(analysis: CompileAnalysis): Seq[String] = analysis match {
    case analysis: Analysis =>
      analysis.infos.allInfos.values.map(_.getMainClasses).flatten.toSeq.sorted
  }

  def consoleProjectTask =
    Def.task {
      ConsoleProject(state.value, (consoleProject / initialCommands).value)(streams.value.log)
      println()
    }

  def consoleTask: Initialize[Task[Unit]] = consoleTask(fullClasspath, console)
  def consoleQuickTask = consoleTask(externalDependencyClasspath, consoleQuick)
  def consoleTask(classpath: TaskKey[Classpath], task: TaskKey[_]): Initialize[Task[Unit]] =
    Def.task {
      val si = (task / scalaInstance).value
      val s = streams.value
      val cpFiles = data((task / classpath).value)
      val fullcp = (cpFiles ++ si.allJars).distinct
      val tempDir = IO.createUniqueDirectory((task / taskTemporaryDirectory).value).toPath
      val loader = ClasspathUtil.makeLoader(fullcp.map(_.toPath), si, tempDir)
      val compiler =
        (task / compilers).value.scalac match {
          case ac: AnalyzingCompiler => ac.onArgs(exported(s, "scala"))
        }
      val sc = (task / scalacOptions).value
      val ic = (task / initialCommands).value
      val cc = (task / cleanupCommands).value
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
      // this stores the eary analysis (again) in case the subproject contains a macro
      setup.earlyAnalysisStore.toOption map { earlyStore =>
        earlyStore.set(contents)
      }
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
    val s = streams.value
    val ci = (compile / compileInputs).value
    val ping = earlyOutputPing.value
    val reporter = (compile / bspReporter).value
    BspCompileTask.compute(bspTargetIdentifier.value, thisProjectRef.value, configuration.value) {
      task =>
        // TODO - Should readAnalysis + saveAnalysis be scoped by the compile task too?
        compileIncrementalTaskImpl(task, s, ci, ping, reporter)
    }
  }
  private val incCompiler = ZincUtil.defaultIncrementalCompiler
  private[sbt] def compileJavaTask: Initialize[Task[CompileResult]] = Def.task {
    val s = streams.value
    val r = compileScalaBackend.value
    val in0 = (compileJava / compileInputs).value
    val in = in0.withPreviousResult(PreviousResult.of(r.analysis, r.setup))
    val reporter = (compile / bspReporter).value
    try {
      if (r.hasModified) {
        val result0 = incCompiler
          .asInstanceOf[sbt.internal.inc.IncrementalCompilerImpl]
          .compileAllJava(in, s.log)
        reporter.sendSuccessReport(result0.analysis())
        result0.withHasModified(result0.hasModified || r.hasModified)
      } else r
    } catch {
      case NonFatal(e) =>
        reporter.sendFailureReport(in.options.sources)
        throw e
    }
  }
  private[this] def compileIncrementalTaskImpl(
      task: BspCompileTask,
      s: TaskStreams,
      ci: Inputs,
      promise: PromiseWrap[Boolean],
      reporter: BuildServerReporter,
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
    def onProgress(s: Setup) = {
      val cp = new BspCompileProgress(task, s.progress.asScala)
      s.withProgress(cp)
    }
    val compilers: Compilers = ci.compilers
    val setup: Setup = ci.setup
    val i = ci
      .withCompilers(onArgs(compilers))
      .withSetup(onProgress(setup))
    try {
      val result = incCompiler.compile(i, s.log)
      reporter.sendSuccessReport(result.getAnalysis)
      result
    } catch {
      case e: Throwable =>
        if (!promise.isCompleted) {
          promise.failure(e)
          ConcurrentRestrictions.cancelAllSentinels()
        }
        reporter.sendFailureReport(ci.options.sources)

        throw e
    } finally {
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
    val useBinary: Boolean = enableBinaryCompileAnalysis.value
    val eapath = earlyCompileAnalysisFile.value.toPath
    val eaOpt =
      if (exportPipelining.value) Some(MixedAnalyzingCompiler.staticCachedStore(eapath, !useBinary))
      else None
    Setup.of(
      lookup,
      (compile / skip).value,
      compileAnalysisFile.value.toPath,
      compilerCache.value,
      incOptions.value,
      (compile / bspReporter).value,
      Some((compile / compileProgress).value).toOptional,
      eaOpt.toOptional,
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
          f1(
            foldMappers(sourcePositionMappers.value, reportAbsolutePath.value, fileConverter.value)
          ),
          compileOrder.value,
          None.toOptional: Optional[NioPath],
          Some(fileConverter.value).toOptional,
          Some(reusableStamper.value).toOptional,
          eoOpt.toOptional,
        )
      },
      compilerReporter := {
        new ManagedLoggedReporter(
          maxErrors.value,
          streams.value.log,
          foldMappers(sourcePositionMappers.value, reportAbsolutePath.value, fileConverter.value)
        )
      },
      compileInputs := {
        val options = compileOptions.value
        val setup = compileIncSetup.value
        val prev = previousCompile.value
        Inputs.of(
          compilers.value,
          options,
          setup,
          prev
        )
      }
    )
  }

  private[sbt] def foldMappers(
      mappers: Seq[Position => Option[Position]],
      reportAbsolutePath: Boolean,
      fc: FileConverter
  ) = {
    def withAbsoluteSource(p: Position): Position =
      if (reportAbsolutePath) toAbsoluteSource(fc)(p) else p

    mappers.foldRight({ p: Position =>
      withAbsoluteSource(p) // Fallback if sourcePositionMappers is empty
    }) {
      (mapper, previousPosition) =>
        { p: Position =>
          // To each mapper we pass the position with the absolute source (only if reportAbsolutePath = true of course)
          mapper(withAbsoluteSource(p)).getOrElse(previousPosition(p))
        }
    }
  }

  private[sbt] def none[A]: Option[A] = (None: Option[A])
  private[sbt] def jnone[A]: Optional[A] = none[A].toOptional
  def compileAnalysisSettings: Seq[Setting[_]] = Seq(
    previousCompile := {
      val setup = compileIncSetup.value
      val useBinary: Boolean = enableBinaryCompileAnalysis.value
      val store = MixedAnalyzingCompiler.staticCachedStore(setup.cacheFile.toPath, !useBinary)
      val prev = store.get().toOption match {
        case Some(contents) =>
          val analysis = Option(contents.getAnalysis).toOptional
          val setup = Option(contents.getMiniSetup).toOptional
          PreviousResult.of(analysis, setup)
        case None => PreviousResult.of(jnone[CompileAnalysis], jnone[MiniSetup])
      }
      prev
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
      val reporter = new ManagedLoggedReporter(
        max,
        streams.value.log,
        foldMappers(spms, reportAbsolutePath.value, fileConverter.value)
      )
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
      val syncDir = crossTarget.value / (prefix(configuration.value.name) + "sync")
      val factory = CacheStoreFactory(syncDir)
      val cacheStore = factory.make("copy-resource")
      val converter = fileConverter.value
      val flt: File => Option[File] = flat(t)
      val transform: File => Option[File] = (f: File) => rebase(dirs, t)(f).orElse(flt(f))
      val mappings: Seq[(File, File)] = resources.value.flatMap {
        case r if !dirs(r) => transform(r).map(r -> _)
        case _             => None
      }
      s.log.debug("Copy resource mappings: " + mappings.mkString("\n\t", "\n\t", ""))
      Sync.sync(cacheStore, fileConverter = converter)(mappings)
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
  def disableAggregate(k: Scoped) = (k / aggregate) :== false

  // 1. runnerSettings is added unscoped via JvmPlugin.
  // 2. In addition it's added scoped to run task.
  lazy val runnerSettings: Seq[Setting[_]] = Seq(runnerTask, forkOptions := forkOptionsTask.value)
  private[this] lazy val newRunnerSettings: Seq[Setting[_]] =
    Seq(runner := ClassLoaders.runner.value, forkOptions := forkOptionsTask.value)

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
      else IvyDependencyResolution(ivyConfiguration.value)
    }
}

object Classpaths {
  import Defaults._
  import Keys._

  def concatDistinct[T](a: Taskable[Seq[T]], b: Taskable[Seq[T]]): Initialize[Task[Seq[T]]] =
    Def.task((a.toTask.value ++ b.toTask.value).distinct)

  def concat[T](a: Taskable[Seq[T]], b: Taskable[Seq[T]]): Initialize[Task[Seq[T]]] =
    Def.task(a.toTask.value ++ b.toTask.value)

  def concatSettings[T](a: Initialize[Seq[T]], b: Initialize[Seq[T]]): Initialize[Seq[T]] =
    Def.setting { a.value ++ b.value }

  def concatDistinct[T]( // forward to widened variant
      a: ScopedTaskable[Seq[T]],
      b: ScopedTaskable[Seq[T]]
  ): Initialize[Task[Seq[T]]] = concatDistinct(a: Taskable[Seq[T]], b)

  def concat[T](a: ScopedTaskable[Seq[T]], b: ScopedTaskable[Seq[T]]): Initialize[Task[Seq[T]]] =
    concat(a: Taskable[Seq[T]], b) // forward to widened variant

  def concatSettings[T](a: SettingKey[Seq[T]], b: SettingKey[Seq[T]]): Initialize[Seq[T]] =
    concatSettings(a: Initialize[Seq[T]], b) // forward to widened variant

  // Included as part of JvmPlugin#projectSettings.
  lazy val configSettings: Seq[Setting[_]] = classpaths ++ Seq(
    products := makeProducts.value,
    pickleProducts := makePickleProducts.value,
    productDirectories := classDirectory.value :: Nil,
    classpathConfiguration := findClasspathConfig(
      internalConfigurationMap.value,
      configuration.value,
      classpathConfiguration.?.value,
      update.value
    )
  )
  private[this] def classpaths: Seq[Setting[_]] =
    Seq(
      externalDependencyClasspath := concat(unmanagedClasspath, managedClasspath).value,
      dependencyClasspath := concat(internalDependencyClasspath, externalDependencyClasspath).value,
      fullClasspath := concatDistinct(exportedProducts, dependencyClasspath).value,
      internalDependencyClasspath := ClasspathImpl.internalDependencyClasspathTask.value,
      unmanagedClasspath := unmanagedDependencies.value,
      managedClasspath := {
        val isMeta = isMetaBuild.value
        val force = reresolveSbtArtifacts.value
        val app = appConfiguration.value
        def isJansiOrJLine(f: File) = f.getName.contains("jline") || f.getName.contains("jansi")
        val scalaInstanceJars = app.provider.scalaProvider.jars.filterNot(isJansiOrJLine)
        val sbtCp = (scalaInstanceJars ++ app.provider.mainClasspath).map(Attributed.blank)
        val mjars = managedJars(
          classpathConfiguration.value,
          classpathTypes.value,
          update.value
        )
        if (isMeta && !force) (mjars ++ sbtCp).distinct
        else mjars
      },
      exportedProducts := ClasspathImpl.trackedExportedProducts(TrackLevel.TrackAlways).value,
      exportedProductsIfMissing := ClasspathImpl
        .trackedExportedProducts(TrackLevel.TrackIfMissing)
        .value,
      exportedProductsNoTracking := ClasspathImpl
        .trackedExportedProducts(TrackLevel.NoTracking)
        .value,
      exportedProductJars := ClasspathImpl.trackedExportedJarProducts(TrackLevel.TrackAlways).value,
      exportedProductJarsIfMissing := ClasspathImpl
        .trackedExportedJarProducts(TrackLevel.TrackIfMissing)
        .value,
      exportedProductJarsNoTracking := ClasspathImpl
        .trackedExportedJarProducts(TrackLevel.NoTracking)
        .value,
      internalDependencyAsJars := internalDependencyJarsTask.value,
      dependencyClasspathAsJars := concat(internalDependencyAsJars, externalDependencyClasspath).value,
      fullClasspathAsJars := concatDistinct(exportedProductJars, dependencyClasspathAsJars).value,
      unmanagedJars := findUnmanagedJars(
        configuration.value,
        unmanagedBase.value,
        (unmanagedJars / includeFilter) value,
        (unmanagedJars / excludeFilter) value
      )
    ).map(exportClasspath) ++ Seq(
      externalDependencyClasspath / outputFileStamps := {
        val stamper = timeWrappedStamper.value
        val converter = fileConverter.value
        externalDependencyClasspath.value flatMap { file0 =>
          val p = file0.data.toPath
          FileStamp(stamper.library(converter.toVirtualFile(p))).map(p -> _)
        }
      },
      dependencyClasspathFiles := data(dependencyClasspath.value).map(_.toPath),
      dependencyClasspathFiles / outputFileStamps := {
        val stamper = timeWrappedStamper.value
        val converter = fileConverter.value
        dependencyClasspathFiles.value.flatMap(
          p => FileStamp(stamper.library(converter.toVirtualFile(p))).map(p -> _)
        )
      },
      dependencyVirtualClasspath := {
        val converter = fileConverter.value
        val cp0 = dependencyClasspath.value
        cp0 map { attr: Attributed[File] =>
          attr map { file =>
            converter.toVirtualFile(file.toPath)
          }
        }
      },
      // Note: invoking this task from shell would block indefinately because it will
      // wait for the upstream compilation to start.
      dependencyPicklePath := {
        // This is a conditional task. Do not refactor.
        if (incOptions.value.pipelining) {
          concat(
            internalDependencyPicklePath,
            Def.task {
              externalDependencyClasspath.value map { attr: Attributed[File] =>
                attr map { file =>
                  val converter = fileConverter.value
                  converter.toVirtualFile(file.toPath)
                }
              }
            }
          ).value
        } else {
          dependencyVirtualClasspath.value
        }
      },
      internalDependencyPicklePath := ClasspathImpl.internalDependencyPicklePathTask.value,
      exportedPickles := ClasspathImpl.exportedPicklesTask.value,
    )

  private[this] def exportClasspath(s: Setting[Task[Classpath]]): Setting[Task[Classpath]] =
    s.mapInitialize(init => Def.task { exportClasspath(streams.value, init.value) })
  private[this] def exportClasspath(s: TaskStreams, cp: Classpath): Classpath = {
    val w = s.text(ExportStream)
    try w.println(Path.makeString(data(cp)))
    finally w.close() // workaround for #937
    cp
  }

  def defaultPackageKeys = Seq(packageBin, packageSrc, packageDoc)
  lazy val defaultPackages: Seq[TaskKey[File]] =
    for (task <- defaultPackageKeys; conf <- Seq(Compile, Test)) yield (conf / task)
  lazy val defaultArtifactTasks: Seq[TaskKey[File]] = makePom +: defaultPackages

  def findClasspathConfig(
      map: Configuration => Configuration,
      thisConfig: Configuration,
      delegated: Option[Configuration],
      report: UpdateReport
  ): Configuration = {
    val defined = report.allConfigurations.toSet
    val search = map(thisConfig) +: (delegated.toList ++ Seq(Compile, Configurations.Default))
    def notFound =
      sys.error(
        "Configuration to use for managed classpath must be explicitly defined when default configurations are not present."
      )
    search find { c =>
      defined contains ConfigRef(c.name)
    } getOrElse notFound
  }

  def packaged(pkgTasks: Seq[TaskKey[File]]): Initialize[Task[Map[Artifact, File]]] =
    enabledOnly(packagedArtifact.toSettingKey, pkgTasks) apply (_.join.map(_.toMap))

  def artifactDefs(pkgTasks: Seq[TaskKey[File]]): Initialize[Seq[Artifact]] =
    enabledOnly(artifact, pkgTasks)

  def enabledOnly[T](key: SettingKey[T], pkgTasks: Seq[TaskKey[File]]): Initialize[Seq[T]] =
    (forallIn(key, pkgTasks) zipWith forallIn(publishArtifact, pkgTasks))(_ zip _ collect {
      case (a, true) => a
    })

  @nowarn
  def forallIn[T](
      key: Scoped.ScopingSetting[SettingKey[T]], // should be just SettingKey[T] (mea culpa)
      pkgTasks: Seq[TaskKey[_]],
  ): Initialize[Seq[T]] =
    pkgTasks.map(pkg => key in pkg.scope in pkg).join

  private[this] def publishGlobalDefaults =
    Defaults.globalDefaults(
      Seq(
        publishMavenStyle :== true,
        publishArtifact :== true,
        (Test / publishArtifact) :== false
      )
    )

  val jvmPublishSettings: Seq[Setting[_]] = Seq(
    artifacts := artifactDefs(defaultArtifactTasks).value,
    packagedArtifacts := packaged(defaultArtifactTasks).value
  ) ++ RemoteCache.projectSettings

  val ivyPublishSettings: Seq[Setting[_]] = publishGlobalDefaults ++ Seq(
    artifacts :== Nil,
    packagedArtifacts :== Map.empty,
    crossTarget := target.value,
    makePom := {
      val config = makePomConfiguration.value
      val publisher = Keys.publisher.value
      publisher.makePomFile(ivyModule.value, config, streams.value.log)
      config.file.get
    },
    (makePom / packagedArtifact) := ((makePom / artifact).value -> makePom.value),
    deliver := deliverTask(makeIvyXmlConfiguration).value,
    deliverLocal := deliverTask(makeIvyXmlLocalConfiguration).value,
    makeIvyXml := deliverTask(makeIvyXmlConfiguration).value,
    publish := publishTask(publishConfiguration).value,
    publishLocal := publishTask(publishLocalConfiguration).value,
    publishM2 := publishTask(publishM2Configuration).value
  )

  private[this] def baseGlobalDefaults =
    Defaults.globalDefaults(
      Seq(
        conflictWarning :== ConflictWarning.default("global"),
        evictionWarningOptions := EvictionWarningOptions.default,
        compatibilityWarningOptions :== CompatibilityWarningOptions.default,
        homepage :== None,
        startYear :== None,
        licenses :== Nil,
        developers :== Nil,
        scmInfo :== None,
        offline :== SysProp.offline,
        defaultConfiguration :== Some(Configurations.Compile),
        dependencyOverrides :== Vector.empty,
        libraryDependencies :== Nil,
        libraryDependencySchemes :== Nil,
        evictionErrorLevel :== Level.Error,
        assumedEvictionErrorLevel :== Level.Info,
        assumedVersionScheme :== VersionScheme.Always,
        assumedVersionSchemeJava :== VersionScheme.Always,
        excludeDependencies :== Nil,
        ivyLoggingLevel := (// This will suppress "Resolving..." logs on Jenkins and Travis.
        if (insideCI.value)
          UpdateLogging.Quiet
        else UpdateLogging.Default),
        ivyXML :== NodeSeq.Empty,
        ivyValidate :== false,
        moduleConfigurations :== Nil,
        publishTo :== None,
        resolvers :== Vector.empty,
        includePluginResolvers :== false,
        useJCenter :== false,
        retrievePattern :== Resolver.defaultRetrievePattern,
        transitiveClassifiers :== Seq(SourceClassifier, DocClassifier),
        sourceArtifactTypes :== Artifact.DefaultSourceTypes.toVector,
        docArtifactTypes :== Artifact.DefaultDocTypes.toVector,
        cleanKeepFiles :== Nil,
        cleanKeepGlobs := {
          val base = appConfiguration.value.baseDirectory.getCanonicalFile
          val dirs = BuildPaths
            .globalLoggingStandard(base) :: BuildPaths.globalTaskDirectoryStandard(base) :: Nil
          dirs.flatMap(d => Glob(d) :: Glob(d, RecursiveGlob) :: Nil)
        },
        fileOutputs :== Nil,
        sbtDependency := {
          val app = appConfiguration.value
          val id = app.provider.id
          val scalaVersion = app.provider.scalaProvider.version
          val binVersion = binaryScalaVersion(scalaVersion)
          val cross = id.crossVersionedValue match {
            case CrossValue.Disabled => Disabled()
            case CrossValue.Full     => CrossVersion.binary
            case CrossValue.Binary   => CrossVersion.full
          }
          val base = ModuleID(id.groupID, id.name, sbtVersion.value).withCrossVersion(cross)
          CrossVersion(scalaVersion, binVersion)(base).withCrossVersion(Disabled())
        },
        shellPrompt := sbt.internal.ui.UITask.NoShellPrompt,
        colorShellPrompt := { (c, s) =>
          shellPrompt.value match {
            case sbt.internal.ui.UITask.NoShellPrompt => shellPromptFromState(c)(s)
            case p                                    => p(s)
          }
        },
        dynamicDependency := { (): Unit },
        transitiveClasspathDependency := { (): Unit },
        transitiveDynamicInputs :== Nil,
      )
    )

  val ivyBaseSettings: Seq[Setting[_]] = baseGlobalDefaults ++ sbtClassifiersTasks ++ Seq(
    conflictWarning := conflictWarning.value.copy(label = Reference.display(thisProjectRef.value)),
    unmanagedBase := baseDirectory.value / "lib",
    normalizedName := Project.normalizeModuleID(name.value),
    isSnapshot := (isSnapshot or version(_ endsWith "-SNAPSHOT")).value,
    description := (description or name).value,
    organization := (organization or normalizedName).value,
    organizationName := (organizationName or organization).value,
    organizationHomepage := (organizationHomepage or homepage).value,
    projectInfo := ModuleInfo(
      name.value,
      description.value,
      homepage.value,
      startYear.value,
      licenses.value.toVector,
      organizationName.value,
      organizationHomepage.value,
      scmInfo.value,
      developers.value.toVector
    ),
    overrideBuildResolvers := appConfiguration(isOverrideRepositories).value,
    externalResolvers := ((
      externalResolvers.?.value,
      resolvers.value,
      appResolvers.value,
      useJCenter.value
    ) match {
      case (Some(delegated), Seq(), _, _) => delegated
      case (_, rs, Some(ars), _)          => ars ++ rs
      case (_, rs, _, uj)                 => Resolver.combineDefaultResolvers(rs.toVector, uj, mavenCentral = true)
    }),
    appResolvers := {
      val ac = appConfiguration.value
      val uj = useJCenter.value
      appRepositories(ac) map { ars =>
        val useMavenCentral = ars contains Resolver.DefaultMavenRepository
        Resolver.reorganizeAppResolvers(ars, uj, useMavenCentral)
      }
    },
    bootResolvers := (appConfiguration map bootRepositories).value,
    fullResolvers :=
      (Def.task {
        val proj = projectResolver.value
        val rs = externalResolvers.value
        def pluginResolvers: Vector[Resolver] =
          buildStructure.value
            .units(thisProjectRef.value.build)
            .unit
            .plugins
            .pluginData
            .resolvers
            .getOrElse(Vector.empty)
        val pr =
          if (includePluginResolvers.value) pluginResolvers
          else Vector.empty
        bootResolvers.value match {
          case Some(repos) if overrideBuildResolvers.value => proj +: repos
          case _ =>
            val base = if (sbtPlugin.value) sbtResolvers.value ++ rs ++ pr else rs ++ pr
            (proj +: base).distinct
        }
      }).value,
    moduleName := normalizedName.value,
    ivyPaths := IvyPaths(baseDirectory.value, bootIvyHome(appConfiguration.value)),
    csrCacheDirectory := {
      val old = csrCacheDirectory.value
      val ac = appConfiguration.value
      val ip = ivyPaths.value
      // if ivyPaths is customized, create coursier-cache directory in it
      if (useCoursier.value) {
        val defaultIvyCache = bootIvyHome(ac)
        if (old != LMCoursier.defaultCacheLocation) old
        else if (ip.ivyHome == defaultIvyCache) old
        else
          ip.ivyHome match {
            case Some(home) => home / "coursier-cache"
            case _          => old
          }
      } else Classpaths.dummyCoursierDirectory(ac)
    },
    dependencyCacheDirectory := {
      val st = state.value
      BuildPaths.getDependencyDirectory(st, BuildPaths.getGlobalBase(st))
    },
    otherResolvers := Resolver.publishMavenLocal +: publishTo.value.toVector,
    projectResolver := projectResolverTask.value,
    projectDependencies := projectDependenciesTask.value,
    // TODO - Is this the appropriate split?  Ivy defines this simply as
    //        just project + library, while the JVM plugin will define it as
    //        having the additional sbtPlugin + autoScala magikz.
    allDependencies := {
      projectDependencies.value ++ libraryDependencies.value
    },
    allExcludeDependencies := excludeDependencies.value,
    scalaModuleInfo := (scalaModuleInfo or (
      Def.setting {
        Option(
          ScalaModuleInfo(
            (update / scalaVersion).value,
            (update / scalaBinaryVersion).value,
            Vector.empty,
            filterImplicit = false,
            checkExplicit = true,
            overrideScalaVersion = true
          ).withScalaOrganization(scalaOrganization.value)
            .withScalaArtifacts(scalaArtifacts.value.toVector)
        )
      }
    )).value,
    makePom / artifactPath := artifactPathSetting((makePom / artifact)).value,
    makePom / publishArtifact := publishMavenStyle.value && publishArtifact.value,
    makePom / artifact := Artifact.pom(moduleName.value),
    projectID := defaultProjectID.value,
    projectID := pluginProjectID.value,
    projectDescriptors := depMap.value,
    updateConfiguration := {
      // Tell the UpdateConfiguration which artifact types are special (for sources and javadocs)
      val specialArtifactTypes = sourceArtifactTypes.value.toSet union docArtifactTypes.value.toSet
      // By default, to retrieve all types *but* these (it's assumed that everything else is binary/resource)
      UpdateConfiguration()
        .withRetrieveManaged(retrieveConfiguration.value)
        .withLogging(ivyLoggingLevel.value)
        .withArtifactFilter(ArtifactTypeFilter.forbid(specialArtifactTypes))
        .withOffline(offline.value)
    },
    retrieveConfiguration := {
      if (retrieveManaged.value)
        Some(
          RetrieveConfiguration()
            .withRetrieveDirectory(managedDirectory.value)
            .withOutputPattern(retrievePattern.value)
            .withSync(retrieveManagedSync.value)
            .withConfigurationsToRetrieve(configurationsToRetrieve.value map { _.toVector })
        )
      else None
    },
    dependencyResolution := dependencyResolutionTask.value,
    publisher := IvyPublisher(ivyConfiguration.value),
    ivyConfiguration := mkIvyConfiguration.value,
    ivyConfigurations := {
      val confs = thisProject.value.configurations
      (confs ++ confs.map(internalConfigurationMap.value) ++ (if (autoCompilerPlugins.value)
                                                                CompilerPlugin :: Nil
                                                              else Nil)).distinct
    },
    ivyConfigurations ++= Configurations.auxiliary,
    ivyConfigurations ++= {
      if (managedScalaInstance.value && scalaHome.value.isEmpty)
        Configurations.ScalaTool :: Configurations.ScalaDocTool :: Nil
      else Nil
    },
    // Coursier needs these
    ivyConfigurations := {
      val confs = ivyConfigurations.value
      val names = confs.map(_.name).toSet
      val extraSources =
        if (names("sources"))
          None
        else
          Some(
            Configuration.of(
              id = "Sources",
              name = "sources",
              description = "",
              isPublic = true,
              extendsConfigs = Vector.empty,
              transitive = false
            )
          )

      val extraDocs =
        if (names("docs"))
          None
        else
          Some(
            Configuration.of(
              id = "Docs",
              name = "docs",
              description = "",
              isPublic = true,
              extendsConfigs = Vector.empty,
              transitive = false
            )
          )

      val use = useCoursier.value
      if (use) confs ++ extraSources.toSeq ++ extraDocs.toSeq
      else confs
    },
    moduleSettings := moduleSettings0.value,
    makePomConfiguration := MakePomConfiguration()
      .withFile((makePom / artifactPath).value)
      .withModuleInfo(projectInfo.value)
      .withExtra(pomExtra.value)
      .withProcess(pomPostProcess.value)
      .withFilterRepositories(pomIncludeRepository.value)
      .withAllRepositories(pomAllRepositories.value)
      .withConfigurations(Configurations.defaultMavenConfigurations),
    makeIvyXmlConfiguration := {
      makeIvyXmlConfig(
        publishMavenStyle.value,
        sbt.Classpaths.deliverPattern(crossTarget.value),
        if (isSnapshot.value) "integration" else "release",
        ivyConfigurations.value.map(c => ConfigRef(c.name)).toVector,
        (publish / checksums).value.toVector,
        ivyLoggingLevel.value,
        isSnapshot.value
      )
    },
    publishConfiguration := {
      val s = streams.value
      val vs = versionScheme.value
      if (vs.isEmpty)
        s.log.warn(
          s"""versionScheme setting is empty; set `ThisBuild / versionScheme := Some("early-semver")`, `Some("semver-spec")` or `Some("pvp")`
             |so tooling can use it for eviction errors etc - https://www.scala-sbt.org/1.x/docs/Publishing.html""".stripMargin
        )
      else ()
      publishConfig(
        publishMavenStyle.value,
        deliverPattern(crossTarget.value),
        if (isSnapshot.value) "integration" else "release",
        ivyConfigurations.value.map(c => ConfigRef(c.name)).toVector,
        (publish / packagedArtifacts).value.toVector,
        (publish / checksums).value.toVector,
        getPublishTo(publishTo.value).name,
        ivyLoggingLevel.value,
        isSnapshot.value
      )
    },
    makeIvyXmlLocalConfiguration := {
      makeIvyXmlConfig(
        false, //publishMavenStyle.value,
        sbt.Classpaths.deliverPattern(crossTarget.value),
        if (isSnapshot.value) "integration" else "release",
        ivyConfigurations.value.map(c => ConfigRef(c.name)).toVector,
        (publish / checksums).value.toVector,
        ivyLoggingLevel.value,
        isSnapshot.value,
        optResolverName = Some("local")
      )
    },
    publishLocalConfiguration := publishConfig(
      false, //publishMavenStyle.value,
      deliverPattern(crossTarget.value),
      if (isSnapshot.value) "integration" else "release",
      ivyConfigurations.value.map(c => ConfigRef(c.name)).toVector,
      (publishLocal / packagedArtifacts).value.toVector,
      (publishLocal / checksums).value.toVector,
      logging = ivyLoggingLevel.value,
      overwrite = isSnapshot.value
    ),
    publishM2Configuration := publishConfig(
      true,
      deliverPattern(crossTarget.value),
      if (isSnapshot.value) "integration" else "release",
      ivyConfigurations.value.map(c => ConfigRef(c.name)).toVector,
      (publishM2 / packagedArtifacts).value.toVector,
      checksums = (publishM2 / checksums).value.toVector,
      resolverName = Resolver.publishMavenLocal.name,
      logging = ivyLoggingLevel.value,
      overwrite = isSnapshot.value
    ),
    ivySbt := ivySbt0.value,
    ivyModule := { val is = ivySbt.value; new is.Module(moduleSettings.value) },
    allCredentials := LMCoursier.allCredentialsTask.value,
    transitiveUpdate := transitiveUpdateTask.value,
    updateCacheName := {
      val binVersion = scalaBinaryVersion.value
      val suffix = if (crossPaths.value) s"_$binVersion" else ""
      s"update_cache$suffix"
    },
    dependencyPositions := dependencyPositionsTask.value,
    update / unresolvedWarningConfiguration := UnresolvedWarningConfiguration(
      dependencyPositions.value
    ),
    updateFull := (updateTask.tag(Tags.Update, Tags.Network)).value,
    update := (updateWithoutDetails("update").tag(Tags.Update, Tags.Network)).value,
    update := {
      val report = update.value
      val log = streams.value.log
      ConflictWarning(conflictWarning.value, report, log)
      report
    },
    update / evictionWarningOptions := evictionWarningOptions.value,
    evicted / evictionWarningOptions := EvictionWarningOptions.full,
    evicted := {
      import ShowLines._
      val report = (updateTask.tag(Tags.Update, Tags.Network)).value
      val log = streams.value.log
      val ew =
        EvictionWarning(ivyModule.value, (evicted / evictionWarningOptions).value, report)
      ew.lines foreach { log.warn(_) }
      ew.infoAllTheThings foreach { log.info(_) }
      ew
    },
  ) ++
    inTask(updateClassifiers)(
      Seq(
        classifiersModule := {
          implicit val key = (m: ModuleID) => (m.organization, m.name, m.revision)
          val projectDeps = projectDependencies.value.iterator.map(key).toSet
          val externalModules = update.value.allModules.filterNot(m => projectDeps contains key(m))
          GetClassifiersModule(
            projectID.value,
            None,
            externalModules,
            ivyConfigurations.value.toVector,
            transitiveClassifiers.value.toVector
          )
        },
        dependencyResolution := dependencyResolutionTask.value,
        csrConfiguration := LMCoursier.updateClassifierConfigurationTask.value,
        TaskGlobal / updateClassifiers := LibraryManagement.updateClassifiersTask.value,
      )
    ) ++ Seq(
    csrProject := CoursierInputsTasks.coursierProjectTask.value,
    csrConfiguration := LMCoursier.coursierConfigurationTask.value,
    csrResolvers := CoursierRepositoriesTasks.coursierResolversTask.value,
    csrRecursiveResolvers := CoursierRepositoriesTasks.coursierRecursiveResolversTask.value,
    csrSbtResolvers := CoursierRepositoriesTasks.coursierSbtResolversTask.value,
    csrInterProjectDependencies := CoursierInputsTasks.coursierInterProjectDependenciesTask.value,
    csrExtraProjects := CoursierInputsTasks.coursierExtraProjectsTask.value,
    csrFallbackDependencies := CoursierInputsTasks.coursierFallbackDependenciesTask.value,
  ) ++
    IvyXml.generateIvyXmlSettings() ++
    LMCoursier.publicationsSetting(Seq(Compile, Test).map(c => c -> CConfiguration(c.name)))

  val jvmBaseSettings: Seq[Setting[_]] = Seq(
    libraryDependencies ++= autoLibraryDependency(
      autoScalaLibrary.value && scalaHome.value.isEmpty && managedScalaInstance.value,
      sbtPlugin.value,
      scalaOrganization.value,
      scalaVersion.value
    ),
    // Override the default to handle mixing in the sbtPlugin + scala dependencies.
    allDependencies := {
      val base = projectDependencies.value ++ libraryDependencies.value
      val isPlugin = sbtPlugin.value
      val sbtdeps =
        (pluginCrossBuild / sbtDependency).value.withConfigurations(Some(Provided.name))
      val pluginAdjust =
        if (isPlugin) sbtdeps +: base
        else base
      val sbtOrg = scalaOrganization.value
      val version = scalaVersion.value
      val extResolvers = externalResolvers.value
      val isScala3M123 = ScalaArtifacts.isScala3M123(version)
      val allToolDeps =
        if (scalaHome.value.isDefined || scalaModuleInfo.value.isEmpty || !managedScalaInstance.value)
          Nil
        else if (!isScala3M123 || extResolvers.contains(Resolver.JCenterRepository)) {
          ScalaArtifacts.toolDependencies(sbtOrg, version) ++
            ScalaArtifacts.docToolDependencies(sbtOrg, version)
        } else ScalaArtifacts.toolDependencies(sbtOrg, version)
      allToolDeps ++ pluginAdjust
    },
    // in case of meta build, exclude all sbt modules from the dependency graph, so we can use the sbt resolved by the launcher
    allExcludeDependencies := {
      val sbtdeps = sbtDependency.value
      val isMeta = isMetaBuild.value
      val force = reresolveSbtArtifacts.value
      val excludes = excludeDependencies.value
      val o = sbtdeps.organization
      val sbtModulesExcludes = Vector[ExclusionRule](
        o % "sbt",
        o %% "scripted-plugin",
        o %% "librarymanagement-core",
        o %% "librarymanagement-ivy",
        o %% "util-logging",
        o %% "util-position",
        o %% "io"
      )
      if (isMeta && !force) excludes.toVector ++ sbtModulesExcludes
      else excludes
    },
    dependencyOverrides ++= {
      val isPlugin = sbtPlugin.value
      val app = appConfiguration.value
      val id = app.provider.id
      val sv = (pluginCrossBuild / sbtVersion).value
      val base = ModuleID(id.groupID, "scripted-plugin", sv).withCrossVersion(CrossVersion.binary)
      if (isPlugin) Seq(base)
      else Seq()
    }
  )

  def warnResolversConflict(ress: Seq[Resolver], log: Logger): Unit = {
    val resset = ress.toSet
    for ((name, r) <- resset groupBy (_.name) if r.size > 1) {
      log.warn(
        "Multiple resolvers having different access mechanism configured with same name '" + name + "'. To avoid conflict, Remove duplicate project resolvers (`resolvers`) or rename publishing resolver (`publishTo`)."
      )
    }
  }

  private[sbt] def errorInsecureProtocol(ress: Seq[Resolver], log: Logger): Unit = {
    val bad = !ress.forall(!_.validateProtocol(log))
    if (bad) {
      sys.error("insecure protocol is unsupported")
    }
  }
  // this warns about .from("http:/...") in ModuleID
  private[sbt] def errorInsecureProtocolInModules(mods: Seq[ModuleID], log: Logger): Unit = {
    val artifacts = mods.flatMap(_.explicitArtifacts.toSeq)
    val bad = !artifacts.forall(!_.validateProtocol(log))
    if (bad) {
      sys.error("insecure protocol is unsupported")
    }
  }

  private[sbt] def defaultProjectID: Initialize[ModuleID] = Def.setting {
    val p0 = ModuleID(organization.value, moduleName.value, version.value)
      .cross((projectID / crossVersion) value)
      .artifacts(artifacts.value: _*)
    val p1 = apiURL.value match {
      case Some(u) => p0.extra(SbtPomExtraProperties.POM_API_KEY -> u.toExternalForm)
      case _       => p0
    }
    val p2 = versionScheme.value match {
      case Some(x) =>
        VersionSchemes.validateScheme(x)
        p1.extra(SbtPomExtraProperties.VERSION_SCHEME_KEY -> x)
      case _ => p1
    }
    p2
  }
  def pluginProjectID: Initialize[ModuleID] =
    Def.setting {
      if (sbtPlugin.value)
        sbtPluginExtra(
          projectID.value,
          (pluginCrossBuild / sbtBinaryVersion).value,
          (pluginCrossBuild / scalaBinaryVersion).value
        )
      else projectID.value
    }
  private[sbt] def ivySbt0: Initialize[Task[IvySbt]] =
    Def.task {
      Credentials.register(credentials.value, streams.value.log)
      new IvySbt(ivyConfiguration.value)
    }
  def moduleSettings0: Initialize[Task[ModuleSettings]] = Def.task {
    val deps = allDependencies.value.toVector
    errorInsecureProtocolInModules(deps, streams.value.log)
    ModuleDescriptorConfiguration(projectID.value, projectInfo.value)
      .withValidate(ivyValidate.value)
      .withScalaModuleInfo(scalaModuleInfo.value)
      .withDependencies(deps)
      .withOverrides(dependencyOverrides.value.toVector)
      .withExcludes(allExcludeDependencies.value.toVector)
      .withIvyXML(ivyXML.value)
      .withConfigurations(ivyConfigurations.value.toVector)
      .withDefaultConfiguration(defaultConfiguration.value)
      .withConflictManager(conflictManager.value)
  }

  private[this] def sbtClassifiersGlobalDefaults =
    Defaults.globalDefaults(
      Seq(
        (updateSbtClassifiers / transitiveClassifiers) ~= (_.filter(_ != DocClassifier))
      )
    )
  def sbtClassifiersTasks =
    sbtClassifiersGlobalDefaults ++
      inTask(updateSbtClassifiers)(
        Seq(
          externalResolvers := {
            val boot = bootResolvers.value
            val explicit = buildStructure.value
              .units(thisProjectRef.value.build)
              .unit
              .plugins
              .pluginData
              .resolvers
            explicit orElse boot getOrElse externalResolvers.value
          },
          ivyConfiguration := InlineIvyConfiguration(
            lock = Option(lock(appConfiguration.value)),
            log = Option(streams.value.log),
            updateOptions = UpdateOptions(),
            paths = Option(ivyPaths.value),
            resolvers = externalResolvers.value.toVector,
            otherResolvers = Vector.empty,
            moduleConfigurations = Vector.empty,
            checksums = checksums.value.toVector,
            managedChecksums = false,
            resolutionCacheDir = Some(crossTarget.value / "resolution-cache"),
          ),
          ivySbt := ivySbt0.value,
          classifiersModule := classifiersModuleTask.value,
          // Redefine scalaVersion and scalaBinaryVersion specifically for the dependency graph used for updateSbtClassifiers task.
          // to fix https://github.com/sbt/sbt/issues/2686
          scalaVersion := appConfiguration.value.provider.scalaProvider.version,
          scalaBinaryVersion := binaryScalaVersion(scalaVersion.value),
          scalaOrganization := ScalaArtifacts.Organization,
          scalaModuleInfo := {
            Some(
              ScalaModuleInfo(
                scalaVersion.value,
                scalaBinaryVersion.value,
                Vector(),
                checkExplicit = false,
                filterImplicit = false,
                overrideScalaVersion = true
              ).withScalaOrganization(scalaOrganization.value)
            )
          },
          dependencyResolution := dependencyResolutionTask.value,
          csrConfiguration := LMCoursier.updateSbtClassifierConfigurationTask.value,
          (TaskGlobal / updateSbtClassifiers) := (Def.task {
            val lm = dependencyResolution.value
            val s = streams.value
            val is = ivySbt.value
            val mod = classifiersModule.value
            val updateConfig0 = updateConfiguration.value
            val updateConfig = updateConfig0
              .withMetadataDirectory(dependencyCacheDirectory.value)
              .withArtifactFilter(
                updateConfig0.artifactFilter.map(af => af.withInverted(!af.inverted))
              )
            val app = appConfiguration.value
            val srcTypes = sourceArtifactTypes.value
            val docTypes = docArtifactTypes.value
            val log = s.log
            val out = is.withIvy(log)(_.getSettings.getDefaultIvyUserDir)
            val uwConfig = (update / unresolvedWarningConfiguration).value
            withExcludes(out, mod.classifiers, lock(app)) { excludes =>
              // val noExplicitCheck = ivy.map(_.withCheckExplicit(false))
              LibraryManagement.transitiveScratch(
                lm,
                "sbt",
                GetClassifiersConfiguration(
                  mod,
                  excludes.toVector,
                  updateConfig,
                  srcTypes.toVector,
                  docTypes.toVector
                ),
                uwConfig,
                log
              ) match {
                case Left(_)   => ???
                case Right(ur) => ur
              }
            }
          } tag (Tags.Update, Tags.Network)).value
        )
      ) ++
      inTask(scalaCompilerBridgeScope)(
        Seq(
          dependencyResolution := dependencyResolutionTask.value,
          csrConfiguration := LMCoursier.scalaCompilerBridgeConfigurationTask.value,
          csrResolvers := CoursierRepositoriesTasks.coursierResolversTask.value,
          externalResolvers := scalaCompilerBridgeResolvers.value,
          ivyConfiguration := InlineIvyConfiguration(
            lock = Option(lock(appConfiguration.value)),
            log = Option(streams.value.log),
            updateOptions = UpdateOptions(),
            paths = Option(ivyPaths.value),
            resolvers = scalaCompilerBridgeResolvers.value.toVector,
            otherResolvers = Vector.empty,
            moduleConfigurations = Vector.empty,
            checksums = checksums.value.toVector,
            managedChecksums = false,
            resolutionCacheDir = Some(crossTarget.value / "bridge-resolution-cache"),
          )
        )
      ) ++ Seq(
      bootIvyConfiguration := (updateSbtClassifiers / ivyConfiguration).value,
      bootDependencyResolution := (updateSbtClassifiers / dependencyResolution).value,
      scalaCompilerBridgeResolvers := {
        val boot = bootResolvers.value
        val explicit = buildStructure.value
          .units(thisProjectRef.value.build)
          .unit
          .plugins
          .pluginData
          .resolvers
        val ext = externalResolvers.value.toVector
        // https://github.com/sbt/sbt/issues/4408
        val xs = (explicit, boot) match {
          case (Some(ex), Some(b)) => (ex.toVector ++ b.toVector).distinct
          case (Some(ex), None)    => ex.toVector
          case (None, Some(b))     => b.toVector
          case _                   => Vector()
        }
        (xs ++ ext).distinct
      },
      scalaCompilerBridgeDependencyResolution := (scalaCompilerBridgeScope / dependencyResolution).value
    )

  def classifiersModuleTask: Initialize[Task[GetClassifiersModule]] =
    Def.task {
      val classifiers = transitiveClassifiers.value
      val ref = thisProjectRef.value
      val pluginClasspath = loadedBuild.value.units(ref.build).unit.plugins.fullClasspath.toVector
      val pluginJars = pluginClasspath.filter(_.data.isFile) // exclude directories: an approximation to whether they've been published
      val pluginIDs: Vector[ModuleID] = pluginJars.flatMap(_ get moduleID.key)
      GetClassifiersModule(
        projectID.value,
        // TODO: Should it be sbt's scalaModuleInfo?
        scalaModuleInfo.value,
        sbtDependency.value +: pluginIDs,
        // sbt is now on Maven Central, so this has changed from sbt 0.13.
        Vector(Configurations.Default) ++ Configurations.default,
        classifiers.toVector
      )
    }

  def deliverTask(config: TaskKey[PublishConfiguration]): Initialize[Task[File]] =
    Def.task {
      Def.unit(update.value)
      IvyActions.deliver(ivyModule.value, config.value, streams.value.log)
    }

  @deprecated("Use variant without delivery key", "1.1.1")
  def publishTask(
      config: TaskKey[PublishConfiguration],
      deliverKey: TaskKey[_],
  ): Initialize[Task[Unit]] =
    publishTask(config)

  def publishTask(config: TaskKey[PublishConfiguration]): Initialize[Task[Unit]] =
    Def.taskIf {
      if ((publish / skip).value) {
        val s = streams.value
        val ref = thisProjectRef.value
        s.log.debug(s"Skipping publish* for ${ref.project}")
      } else {
        val s = streams.value
        IvyActions.publish(ivyModule.value, config.value, s.log)
      }
    } tag (Tags.Publish, Tags.Network)

  val moduleIdJsonKeyFormat: sjsonnew.JsonKeyFormat[ModuleID] =
    new sjsonnew.JsonKeyFormat[ModuleID] {
      import LibraryManagementCodec._
      import sjsonnew.support.scalajson.unsafe._
      val moduleIdFormat: JsonFormat[ModuleID] = implicitly[JsonFormat[ModuleID]]
      def write(key: ModuleID): String =
        CompactPrinter(Converter.toJsonUnsafe(key)(moduleIdFormat))
      def read(key: String): ModuleID =
        Converter.fromJsonUnsafe[ModuleID](Parser.parseUnsafe(key))(moduleIdFormat)
    }

  def withExcludes(out: File, classifiers: Seq[String], lock: xsbti.GlobalLock)(
      f: Map[ModuleID, Vector[ConfigRef]] => UpdateReport
  ): UpdateReport = LibraryManagement.withExcludes(out, classifiers, lock)(f)

  /**
   * Substitute unmanaged jars for managed jars when the major.minor parts of
   * the version are the same for:
   *   1. The Scala version and the `scalaHome` (unmanaged) version are equal.
   *   2. The Scala version and the `declared` (managed) version are equal.
   *
   * Equality is weak, that is, no version qualifier is checked.
   */
  private def unmanagedJarsTask(scalaVersion: String, unmanagedVersion: String, jars: Seq[File]) = {
    (subVersion0: String) =>
      val scalaV = partialVersion(scalaVersion)
      val managedV = partialVersion(subVersion0)
      val unmanagedV = partialVersion(unmanagedVersion)
      (managedV, unmanagedV, scalaV) match {
        case (Some(mv), Some(uv), _) if mv == uv => jars
        case (Some(mv), _, Some(sv)) if mv == sv => jars
        case _                                   => Nil
      }
  }

  def updateTask: Initialize[Task[UpdateReport]] = updateTask0("updateFull", true, true)
  def updateWithoutDetails(label: String): Initialize[Task[UpdateReport]] =
    updateTask0(label, false, false)

  /**
   * cacheLabel - label to identify an update cache
   * includeCallers - include the caller information
   * includeDetails - include module reports for the evicted modules
   */
  private def updateTask0(
      cacheLabel: String,
      includeCallers: Boolean,
      includeDetails: Boolean
  ): Initialize[Task[UpdateReport]] = Def.task {
    val s = streams.value
    val cacheDirectory = crossTarget.value / cacheLabel / updateCacheName.value

    val cacheStoreFactory: CacheStoreFactory = {
      val factory =
        state.value.get(Keys.cacheStoreFactoryFactory).getOrElse(InMemoryCacheStore.factory(0))
      factory(cacheDirectory.toPath)
    }

    val isRoot = executionRoots.value contains resolvedScoped.value
    val shouldForce = isRoot || {
      forceUpdatePeriod.value match {
        case None => false
        case Some(period) =>
          val fullUpdateOutput = cacheDirectory / "out"
          val now = System.currentTimeMillis
          val diff = now - IO.getModifiedTimeOrZero(fullUpdateOutput)
          val elapsedDuration = new FiniteDuration(diff, TimeUnit.MILLISECONDS)
          fullUpdateOutput.exists() && elapsedDuration > period
      }
    }

    val providedScalaJars: String => Seq[File] = {
      val scalaProvider = appConfiguration.value.provider.scalaProvider
      Defaults.unmanagedScalaInstanceOnly.value match {
        case Some(instance) =>
          unmanagedJarsTask(scalaVersion.value, instance.version, instance.allJars)
        case None =>
          (subVersion: String) =>
            if (scalaProvider.version == subVersion) scalaProvider.jars else Nil
      }
    }

    val state0 = state.value
    val updateConf = {
      // Log captures log messages at all levels, except ivy logs.
      // Use full level when debug is enabled so that ivy logs are shown.
      import UpdateLogging.{ Default, DownloadOnly, Full }
      val conf = updateConfiguration.value
      val maybeUpdateLevel = (update / logLevel).?.value
      val conf1 = maybeUpdateLevel.orElse(state0.get(logLevel.key)) match {
        case Some(Level.Debug) if conf.logging == Default => conf.withLogging(logging = Full)
        case Some(_) if conf.logging == Default           => conf.withLogging(logging = DownloadOnly)
        case _                                            => conf
      }

      // logical clock is folded into UpdateConfiguration
      conf1
        .withLogicalClock(LogicalClock(state0.hashCode))
        .withMetadataDirectory(dependencyCacheDirectory.value)
    }

    val extracted = Project.extract(state0)
    val isPlugin = sbtPlugin.value
    val thisRef = thisProjectRef.value
    val label =
      if (isPlugin) Reference.display(thisRef)
      else Def.displayRelativeReference(extracted.currentRef, thisRef)

    LibraryManagement.cachedUpdate(
      // LM API
      lm = dependencyResolution.value,
      // Ivy-free ModuleDescriptor
      module = ivyModule.value,
      cacheStoreFactory = cacheStoreFactory,
      label = label,
      updateConf,
      substituteScalaFiles(scalaOrganization.value, _)(providedScalaJars),
      skip = (update / skip).value,
      force = shouldForce,
      depsUpdated = transitiveUpdate.value.exists(!_.stats.cached),
      uwConfig = (update / unresolvedWarningConfiguration).value,
      evictionLevel = evictionErrorLevel.value,
      versionSchemeOverrides = libraryDependencySchemes.value,
      assumedEvictionErrorLevel = assumedEvictionErrorLevel.value,
      assumedVersionScheme = assumedVersionScheme.value,
      assumedVersionSchemeJava = assumedVersionSchemeJava.value,
      mavenStyle = publishMavenStyle.value,
      compatWarning = compatibilityWarningOptions.value,
      includeCallers = includeCallers,
      includeDetails = includeDetails,
      log = s.log
    )
  }

  private[sbt] def dependencyPositionsTask: Initialize[Task[Map[ModuleID, SourcePosition]]] =
    Def.task {
      val projRef = thisProjectRef.value
      val st = state.value
      val s = streams.value
      val cacheStoreFactory = s.cacheStoreFactory sub updateCacheName.value
      import sbt.librarymanagement.LibraryManagementCodec._
      def modulePositions: Map[ModuleID, SourcePosition] =
        try {
          val extracted = (Project extract st)
          val sk = (projRef / Zero / Zero / libraryDependencies).scopedKey
          val empty = extracted.structure.data.set(sk.scope, sk.key, Nil)
          val settings = extracted.structure.settings filter { s: Setting[_] =>
            (s.key.key == libraryDependencies.key) &&
            (s.key.scope.project == Select(projRef))
          }
          Map(settings flatMap {
            case s: Setting[Seq[ModuleID]] @unchecked =>
              s.init.evaluate(empty) map { _ -> s.pos }
          }: _*)
        } catch {
          case NonFatal(_) => Map()
        }

      val outCacheStore = cacheStoreFactory make "output_dsp"
      val f = Tracked.inputChanged(cacheStoreFactory make "input_dsp") {
        (inChanged: Boolean, in: Seq[ModuleID]) =>
          implicit val NoPositionFormat: JsonFormat[NoPosition.type] = asSingleton(NoPosition)
          implicit val LinePositionFormat: IsoLList.Aux[LinePosition, String :*: Int :*: LNil] =
            LList.iso(
              { l: LinePosition =>
                ("path", l.path) :*: ("startLine", l.startLine) :*: LNil
              }, { in: String :*: Int :*: LNil =>
                LinePosition(in.head, in.tail.head)
              }
            )
          implicit val LineRangeFormat: IsoLList.Aux[LineRange, Int :*: Int :*: LNil] = LList.iso(
            { l: LineRange =>
              ("start", l.start) :*: ("end", l.end) :*: LNil
            }, { in: Int :*: Int :*: LNil =>
              LineRange(in.head, in.tail.head)
            }
          )
          implicit val RangePositionFormat
              : IsoLList.Aux[RangePosition, String :*: LineRange :*: LNil] = LList.iso(
            { r: RangePosition =>
              ("path", r.path) :*: ("range", r.range) :*: LNil
            }, { in: String :*: LineRange :*: LNil =>
              RangePosition(in.head, in.tail.head)
            }
          )
          implicit val SourcePositionFormat: JsonFormat[SourcePosition] =
            unionFormat3[SourcePosition, NoPosition.type, LinePosition, RangePosition]

          implicit val midJsonKeyFmt: sjsonnew.JsonKeyFormat[ModuleID] = moduleIdJsonKeyFormat
          val outCache =
            Tracked.lastOutput[Seq[ModuleID], Map[ModuleID, SourcePosition]](outCacheStore) {
              case (_, Some(out)) if !inChanged => out
              case _                            => modulePositions
            }
          outCache(in)
      }
      f(libraryDependencies.value)
    }

  /*
    // can't cache deliver/publish easily since files involved are hidden behind patterns.  publish will be difficult to verify target-side anyway
    def cachedPublish(cacheFile: File)(g: (IvySbt#Module, PublishConfiguration) => Unit, module: IvySbt#Module, config: PublishConfiguration) => Unit =
    { case module :+: config :+: HNil =>
    /*	implicit val publishCache = publishIC
      val f = cached(cacheFile) { (conf: IvyConfiguration, settings: ModuleSettings, config: PublishConfiguration) =>*/
          g(module, config)
      /*}
      f(module.owner.configuration :+: module.moduleSettings :+: config :+: HNil)*/
    }*/

  def defaultRepositoryFilter: MavenRepository => Boolean = repo => !repo.root.startsWith("file:")

  def getPublishTo(repo: Option[Resolver]): Resolver =
    repo getOrElse sys.error("Repository for publishing is not specified.")

  def publishConfig(
      publishMavenStyle: Boolean,
      deliverIvyPattern: String,
      status: String,
      configurations: Vector[ConfigRef],
      artifacts: Vector[(Artifact, File)],
      checksums: Vector[String],
      resolverName: String = "local",
      logging: UpdateLogging = UpdateLogging.DownloadOnly,
      overwrite: Boolean = false
  ) =
    PublishConfiguration(
      publishMavenStyle,
      deliverIvyPattern,
      status,
      configurations,
      resolverName,
      artifacts,
      checksums,
      logging,
      overwrite
    )

  def makeIvyXmlConfig(
      publishMavenStyle: Boolean,
      deliverIvyPattern: String,
      status: String,
      configurations: Vector[ConfigRef],
      checksums: Vector[String],
      logging: sbt.librarymanagement.UpdateLogging = UpdateLogging.DownloadOnly,
      overwrite: Boolean = false,
      optResolverName: Option[String] = None
  ) =
    PublishConfiguration(
      publishMavenStyle,
      Some(deliverIvyPattern),
      Some(status),
      Some(configurations),
      optResolverName,
      Vector.empty,
      checksums,
      Some(logging),
      overwrite
    )

  def deliverPattern(outputPath: File): String =
    (outputPath / "[artifact]-[revision](-[classifier]).[ext]").absolutePath

  private[sbt] def isScala2Scala3Sandwich(sbv1: String, sbv2: String): Boolean = {
    def compare(a: String, b: String): Boolean =
      a == "2.13" && (b.startsWith("0.") || b.startsWith("3"))
    compare(sbv1, sbv2) || compare(sbv2, sbv1)
  }

  def projectDependenciesTask: Initialize[Task[Seq[ModuleID]]] =
    Def.task {
      val sbv = scalaBinaryVersion.value
      val ref = thisProjectRef.value
      val data = settingsData.value
      val deps = buildDependencies.value
      deps.classpath(ref) flatMap { dep =>
        for {
          depProjId <- (dep.project / projectID).get(data)
          depSBV <- (dep.project / scalaBinaryVersion).get(data)
          depCross <- (dep.project / crossVersion).get(data)
        } yield {
          depCross match {
            case b: CrossVersion.Binary if isScala2Scala3Sandwich(sbv, depSBV) =>
              depProjId
                .withCrossVersion(CrossVersion.constant(b.prefix + depSBV))
                .withConfigurations(dep.configuration)
                .withExplicitArtifacts(Vector.empty)
            case _ =>
              depProjId.withConfigurations(dep.configuration).withExplicitArtifacts(Vector.empty)
          }
        }
      }
    }

  private[sbt] def depMap: Initialize[Task[Map[ModuleRevisionId, ModuleDescriptor]]] =
    Def.taskDyn {
      depMap(
        buildDependencies.value classpathTransitiveRefs thisProjectRef.value,
        settingsData.value,
        streams.value.log
      )
    }

  @nowarn
  private[sbt] def depMap(
      projects: Seq[ProjectRef],
      data: Settings[Scope],
      log: Logger
  ): Initialize[Task[Map[ModuleRevisionId, ModuleDescriptor]]] =
    Def.value {
      projects.flatMap(ivyModule in _ get data).join.map { mod =>
        mod map { _.dependencyMapping(log) } toMap;
      }
    }

  def projectResolverTask: Initialize[Task[Resolver]] =
    projectDescriptors map { m =>
      val resolver = new ProjectResolver(ProjectResolver.InterProject, m)
      new RawRepository(resolver, resolver.getName)
    }

  def analyzed[T](data: T, analysis: CompileAnalysis) = ClasspathImpl.analyzed[T](data, analysis)
  def makeProducts: Initialize[Task[Seq[File]]] = Def.task {
    val c = fileConverter.value
    Def.unit(copyResources.value)
    Def.unit(compile.value)

    c.toPath(backendOutput.value).toFile :: Nil
  }

  private[sbt] def makePickleProducts: Initialize[Task[Seq[VirtualFile]]] = Def.task {
    // This is a conditional task.
    if (earlyOutputPing.await.value) {
      // TODO: copyResources.value
      earlyOutput.value :: Nil
    } else {
      val c = fileConverter.value
      products.value map { x: File =>
        c.toVirtualFile(x.toPath)
      }
    }
  }

  def constructBuildDependencies: Initialize[BuildDependencies] =
    loadedBuild(lb => BuildUtil.dependencies(lb.units))

  @deprecated("not used", "1.4.0")
  def internalDependencies: Initialize[Task[Classpath]] =
    ClasspathImpl.internalDependencyClasspathTask

  def internalDependencyJarsTask: Initialize[Task[Classpath]] =
    ClasspathImpl.internalDependencyJarsTask
  def unmanagedDependencies: Initialize[Task[Classpath]] = ClasspathImpl.unmanagedDependenciesTask
  def mkIvyConfiguration: Initialize[Task[InlineIvyConfiguration]] =
    Def.task {
      val (rs, other) = (fullResolvers.value.toVector, otherResolvers.value.toVector)
      val s = streams.value
      warnResolversConflict(rs ++: other, s.log)
      errorInsecureProtocol(rs ++: other, s.log)
      InlineIvyConfiguration()
        .withPaths(ivyPaths.value)
        .withResolvers(rs)
        .withOtherResolvers(other)
        .withModuleConfigurations(moduleConfigurations.value.toVector)
        .withLock(lock(appConfiguration.value))
        .withChecksums((update / checksums).value.toVector)
        .withResolutionCacheDir(crossTarget.value / "resolution-cache")
        .withUpdateOptions(updateOptions.value)
        .withLog(s.log)
    }

  def interSort(
      projectRef: ProjectRef,
      conf: Configuration,
      data: Settings[Scope],
      deps: BuildDependencies
  ): Seq[(ProjectRef, String)] = ClasspathImpl.interSort(projectRef, conf, data, deps)

  def interSortConfigurations(
      projectRef: ProjectRef,
      conf: Configuration,
      data: Settings[Scope],
      deps: BuildDependencies
  ): Seq[(ProjectRef, ConfigRef)] =
    interSort(projectRef, conf, data, deps).map {
      case (projectRef, configName) => (projectRef, ConfigRef(configName))
    }

  def mapped(
      confString: Option[String],
      masterConfs: Seq[String],
      depConfs: Seq[String],
      default: String,
      defaultMapping: String
  ): String => Seq[String] =
    ClasspathImpl.mapped(confString, masterConfs, depConfs, default, defaultMapping)

  def parseMapping(
      confString: String,
      masterConfs: Seq[String],
      depConfs: Seq[String],
      default: String => Seq[String]
  ): String => Seq[String] =
    ClasspathImpl.parseMapping(confString, masterConfs, depConfs, default)

  def parseSingleMapping(
      masterConfs: Seq[String],
      depConfs: Seq[String],
      default: String => Seq[String]
  )(confString: String): String => Seq[String] =
    ClasspathImpl.parseSingleMapping(masterConfs, depConfs, default)(confString)

  def union[A, B](maps: Seq[A => Seq[B]]): A => Seq[B] =
    ClasspathImpl.union[A, B](maps)

  def parseList(s: String, allConfs: Seq[String]): Seq[String] =
    ClasspathImpl.parseList(s, allConfs)

  def replaceWildcard(allConfs: Seq[String])(conf: String): Seq[String] =
    ClasspathImpl.replaceWildcard(allConfs)(conf)

  def missingConfiguration(in: String, conf: String) =
    sys.error("Configuration '" + conf + "' not defined in '" + in + "'")
  def allConfigs(conf: Configuration): Seq[Configuration] = ClasspathImpl.allConfigs(conf)

  def getConfigurations(p: ResolvedReference, data: Settings[Scope]): Seq[Configuration] =
    ClasspathImpl.getConfigurations(p, data)
  def confOpt(configurations: Seq[Configuration], conf: String): Option[Configuration] =
    ClasspathImpl.confOpt(configurations, conf)

  def unmanagedLibs(dep: ResolvedReference, conf: String, data: Settings[Scope]): Task[Classpath] =
    ClasspathImpl.unmanagedLibs(dep, conf, data)

  def getClasspath(
      key: TaskKey[Classpath],
      dep: ResolvedReference,
      conf: String,
      data: Settings[Scope]
  ): Task[Classpath] =
    ClasspathImpl.getClasspath(key, dep, conf, data)

  def defaultConfigurationTask(p: ResolvedReference, data: Settings[Scope]): Configuration =
    flatten((p / defaultConfiguration) get data) getOrElse Configurations.Default

  def flatten[T](o: Option[Option[T]]): Option[T] = o flatMap idFun

  val sbtIvySnapshots: URLRepository = Resolver.sbtIvyRepo("snapshots")
  val typesafeReleases: URLRepository =
    Resolver.typesafeIvyRepo("releases").withName("typesafe-alt-ivy-releases")
  val sbtPluginReleases: URLRepository = Resolver.sbtPluginRepo("releases")
  val sbtMavenSnapshots: MavenRepository =
    MavenRepository("sbt-maven-snapshot", Resolver.SbtRepositoryRoot + "/" + "maven-snapshots/")

  def modifyForPlugin(plugin: Boolean, dep: ModuleID): ModuleID =
    if (plugin) dep.withConfigurations(Some(Provided.name)) else dep

  def autoLibraryDependency(
      auto: Boolean,
      plugin: Boolean,
      org: String,
      version: String
  ): Seq[ModuleID] =
    if (auto)
      modifyForPlugin(plugin, ScalaArtifacts.libraryDependency(org, version)) :: Nil
    else
      Nil

  def addUnmanagedLibrary: Seq[Setting[_]] =
    Seq((Compile / unmanagedJars) ++= unmanagedScalaLibrary.value)

  def unmanagedScalaLibrary: Initialize[Task[Seq[File]]] = Def.taskDyn {
    if (autoScalaLibrary.value && scalaHome.value.isDefined)
      Def.task { scalaInstance.value.libraryJars } else
      Def.task { Nil }
  }

  import DependencyFilter._
  def managedJars(config: Configuration, jarTypes: Set[String], up: UpdateReport): Classpath =
    up.filter(configurationFilter(config.name) && artifactFilter(`type` = jarTypes))
      .toSeq
      .map {
        case (_, module, art, file) =>
          Attributed(file)(
            AttributeMap.empty
              .put(artifact.key, art)
              .put(moduleID.key, module)
              .put(configuration.key, config)
          )
      }
      .distinct

  def findUnmanagedJars(
      config: Configuration,
      base: File,
      filter: FileFilter,
      excl: FileFilter
  ): Classpath = {
    (base * (filter -- excl) +++ (base / config.name).descendantsExcept(filter, excl)).classpath
  }
  @deprecated(
    "The method only works for Scala 2, use the overloaded version to support both Scala 2 and Scala 3",
    "1.1.5"
  )
  def autoPlugins(report: UpdateReport, internalPluginClasspath: Seq[File]): Seq[String] =
    autoPlugins(report, internalPluginClasspath, isDotty = false)

  def autoPlugins(
      report: UpdateReport,
      internalPluginClasspath: Seq[File],
      isDotty: Boolean
  ): Seq[String] = {
    import sbt.internal.inc.classpath.ClasspathUtil.compilerPlugins
    val pluginClasspath = report.matching(configurationFilter(CompilerPlugin.name)) ++ internalPluginClasspath
    val plugins = compilerPlugins(pluginClasspath.map(_.toPath), isDotty)
    plugins.map("-Xplugin:" + _.toAbsolutePath.toString).toSeq
  }

  private[this] lazy val internalCompilerPluginClasspath: Initialize[Task[Classpath]] =
    Def.taskDyn {
      val ref = thisProjectRef.value
      val data = settingsData.value
      val deps = buildDependencies.value
      ClasspathImpl.internalDependenciesImplTask(
        ref,
        CompilerPlugin,
        CompilerPlugin,
        data,
        deps,
        TrackLevel.TrackAlways,
        streams.value.log
      )
    }

  lazy val compilerPluginConfig = Seq(
    scalacOptions := {
      val options = scalacOptions.value
      val newPlugins = autoPlugins(
        update.value,
        internalCompilerPluginClasspath.value.files,
        ScalaInstance.isDotty(scalaVersion.value)
      )
      val existing = options.toSet
      if (autoCompilerPlugins.value) options ++ newPlugins.filterNot(existing) else options
    }
  )

  def substituteScalaFiles(scalaOrg: String, report: UpdateReport)(
      scalaJars: String => Seq[File]
  ): UpdateReport =
    report.substitute { (configuration, module, arts) =>
      if (module.organization == scalaOrg) {
        val jarName = module.name + ".jar"
        val replaceWith = scalaJars(module.revision).toVector
          .filter(_.getName == jarName)
          .map(f => (Artifact(f.getName.stripSuffix(".jar")), f))
        if (replaceWith.isEmpty) arts else replaceWith
      } else
        arts
    }

  // try/catch for supporting earlier launchers
  def bootIvyHome(app: xsbti.AppConfiguration): Option[File] =
    try {
      Option(app.provider.scalaProvider.launcher.ivyHome)
    } catch {
      case _: NoSuchMethodError => None
    }

  def bootChecksums(app: xsbti.AppConfiguration): Vector[String] =
    try {
      app.provider.scalaProvider.launcher.checksums.toVector
    } catch {
      case _: NoSuchMethodError => IvySbt.DefaultChecksums
    }

  def isOverrideRepositories(app: xsbti.AppConfiguration): Boolean =
    try app.provider.scalaProvider.launcher.isOverrideRepositories
    catch { case _: NoSuchMethodError => false }

  /** Loads the `appRepositories` configured for this launcher, if supported. */
  def appRepositories(app: xsbti.AppConfiguration): Option[Vector[Resolver]] =
    try {
      Some(app.provider.scalaProvider.launcher.appRepositories.toVector map bootRepository)
    } catch {
      case _: NoSuchMethodError => None
    }

  def bootRepositories(app: xsbti.AppConfiguration): Option[Vector[Resolver]] =
    try {
      Some(app.provider.scalaProvider.launcher.ivyRepositories.toVector map bootRepository)
    } catch {
      case _: NoSuchMethodError => None
    }

  // This is a place holder in case someone doesn't want to use Coursier
  private[sbt] def dummyCoursierDirectory(app: xsbti.AppConfiguration): File = {
    val base = app.baseDirectory.getCanonicalFile
    base / "target" / "coursier-temp"
  }

  private[this] def mavenCompatible(ivyRepo: xsbti.IvyRepository): Boolean =
    try {
      ivyRepo.mavenCompatible
    } catch { case _: NoSuchMethodError => false }

  private[this] def skipConsistencyCheck(ivyRepo: xsbti.IvyRepository): Boolean =
    try {
      ivyRepo.skipConsistencyCheck
    } catch { case _: NoSuchMethodError => false }

  private[this] def descriptorOptional(ivyRepo: xsbti.IvyRepository): Boolean =
    try {
      ivyRepo.descriptorOptional
    } catch { case _: NoSuchMethodError => false }

  // for forward-compatibility with launcher.jar prior to 1.3.11
  private[this] def mavenRepoAllowInsecureProtocol(mavenRepo: xsbti.MavenRepository): Boolean =
    try {
      mavenRepo.allowInsecureProtocol
    } catch { case _: NoSuchMethodError => false }

  // for forward-compatibility with launcher.jar prior to 1.3.11
  private[this] def allowInsecureProtocol(ivyRepo: xsbti.IvyRepository): Boolean =
    try {
      ivyRepo.allowInsecureProtocol
    } catch { case _: NoSuchMethodError => false }

  @nowarn
  private[this] def bootRepository(repo: xsbti.Repository): Resolver = {
    import xsbti.Predefined
    repo match {
      case m: xsbti.MavenRepository =>
        MavenRepository(m.id, m.url.toString)
          .withAllowInsecureProtocol(mavenRepoAllowInsecureProtocol(m))
      case i: xsbti.IvyRepository =>
        val patterns = Patterns(
          Vector(i.ivyPattern),
          Vector(i.artifactPattern),
          mavenCompatible(i),
          descriptorOptional(i),
          skipConsistencyCheck(i)
        )
        i.url.getProtocol match {
          case "file" =>
            // This hackery is to deal suitably with UNC paths on Windows. Once we can assume Java7, Paths should save us from this.
            val file = IO.toFile(i.url)
            Resolver.file(i.id, file)(patterns)
          case _ =>
            Resolver
              .url(i.id, i.url)(patterns)
              .withAllowInsecureProtocol(allowInsecureProtocol(i))
        }
      case p: xsbti.PredefinedRepository =>
        p.id match {
          case Predefined.Local                => Resolver.defaultLocal
          case Predefined.MavenLocal           => Resolver.mavenLocal
          case Predefined.MavenCentral         => Resolver.DefaultMavenRepository
          case Predefined.ScalaToolsReleases   => Resolver.ScalaToolsReleases
          case Predefined.ScalaToolsSnapshots  => Resolver.ScalaToolsSnapshots
          case Predefined.SonatypeOSSReleases  => Resolver.sonatypeRepo("releases")
          case Predefined.SonatypeOSSSnapshots => Resolver.sonatypeRepo("snapshots")
          case unknown =>
            sys.error(
              "Unknown predefined resolver '" + unknown + "'.  This resolver may only be supported in newer sbt versions."
            )
        }
    }
  }

  def shellPromptFromState: State => String = shellPromptFromState(ITerminal.console.isColorEnabled)
  def shellPromptFromState(isColorEnabled: Boolean): State => String = { s: State =>
    val extracted = Project.extract(s)
    (extracted.currentRef / name).get(extracted.structure.data) match {
      case Some(name) =>
        s"sbt:$name" + Def.withColor(s"> ", Option(scala.Console.CYAN), isColorEnabled)
      case _ => "> "
    }
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
      (GlobalScope / setting) ~= (_ compose f)
    Seq(compose(onLoad, add), compose(onUnload, remove))
  }

  /**
   * Adds Dependency tree plugin.
   */
  def addDependencyTreePlugin: Setting[Seq[ModuleID]] =
    libraryDependencies += sbtPluginExtra(
      ModuleID("org.scala-sbt", "sbt-dependency-tree", sbtVersion.value),
      sbtBinaryVersion.value,
      scalaBinaryVersion.value
    )

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
      val scalaV = (update / scalaBinaryVersion).value
      sbtPluginExtra(dependency, sbtVersion, scalaV)
    }

  /**
   * Adds `dependency` as an sbt plugin for the sbt and Scala versions configured by
   * `sbtBinaryVersion` and `scalaBinaryVersion` scoped to `update`.
   */
  def addSbtPlugin(dependency: ModuleID): Setting[Seq[ModuleID]] =
    libraryDependencies += {
      val sbtV = (pluginCrossBuild / sbtBinaryVersion).value
      val scalaV = (update / scalaBinaryVersion).value
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
    val pkgd = packagedArtifacts := packagedArtifacts.value.updated(a, taskDef.value)
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
    val pkgd = packagedArtifacts := packagedArtifacts.value.updated(artLocal.value, taskLocal.value)
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

  @deprecated(
    "externalIvyFile is not supported by Couriser, and will be removed in the future",
    since = "1.5.0"
  )
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

  @deprecated(
    "externalPom is not supported by Coursier, and will be removed in the future",
    since = "1.5.0"
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
      val r = (config / run / runner).value
      val cp = (config / fullClasspath).value
      val args = spaceDelimited().parsed
      r.run(mainClass, data(cp), baseArguments ++ args, streams.value.log).get
    }

  def runTask(
      config: Configuration,
      mainClass: String,
      arguments: String*
  ): Initialize[Task[Unit]] =
    Def.task {
      val cp = (config / fullClasspath).value
      val r = (config / run / runner).value
      val s = streams.value
      r.run(mainClass, data(cp), arguments, s.log).get
    }

  // public API
  /** Returns a vector of settings that create custom run input task. */
  @nowarn
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
    @nowarn
    def inputTask[T](f: TaskKey[Seq[String]] => Initialize[Task[T]]): Initialize[InputTask[T]] =
      InputTask.apply(Def.value((s: State) => Def.spaceDelimited()))(f)

    Vector(
      scoped := inputTask { result =>
        initScoped(
          scoped.scopedKey,
          ClassLoaders.runner mapReferenced Project.mapScope(s => s.in(config))
        ).zipWith(Def.task { ((config / fullClasspath).value, streams.value, result.value) }) {
          (rTask, t) =>
            (t, rTask) map {
              case ((cp, s, args), r) =>
                r.run(mainClass, data(cp), baseArguments ++ args, s.log).get
            }
        }
      }.evaluated
    ) ++ inTask(scoped)((config / forkOptions) := forkOptionsTask.value)
  }

  // public API
  /** Returns a vector of settings that create custom run task. */
  @nowarn
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
      ).zipWith(Def.task { ((config / fullClasspath).value, streams.value) }) {
          case (rTask, t) =>
            (t, rTask) map {
              case ((cp, s), r) =>
                r.run(mainClass, data(cp), arguments, s.log).get
            }
        }
        .value
    ) ++ inTask(scoped)((config / forkOptions) := forkOptionsTask.value)

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
