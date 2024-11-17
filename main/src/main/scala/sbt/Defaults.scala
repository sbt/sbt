/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.io.{ File, PrintWriter }
import java.net.URL
import java.nio.file.{ Files, Paths, Path => NioPath }
import java.util.Optional
import java.util.concurrent.TimeUnit
import lmcoursier.CoursierDependencyResolution
import lmcoursier.definitions.{ Configuration => CConfiguration }
import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.logging.log4j.core.{ Appender => XAppender }
import org.scalasbt.ipcsocket.Win32SecurityLevel
import sbt.Def.{ Initialize, ScopedKey, Setting, SettingsDefinition, parsed }
import sbt.Keys._
import sbt.OptionSyntax._
import sbt.Project.{
  inScope,
  inTask,
  // richInitialize,
  // richInitialize Task,
  // richTaskSessionVar,
  // sbtRichTaskPromise
}
import sbt.ProjectExtra.*
import sbt.Scope.{ GlobalScope, ThisBuildScope, ThisScope, fillTaskAxis }
import sbt.State.StateOpsImpl
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
import sbt.std.TaskExtra.*
import sbt.testing.{ AnnotatedFingerprint, Framework, Runner, SubclassFingerprint }
import sbt.util.CacheImplicits.given
import sbt.util.InterfaceUtil.{ t2, toJavaFunction => f1 }
import sbt.util._
import sjsonnew._

import scala.annotation.nowarn
import scala.collection.immutable.ListMap
import scala.concurrent.duration._
import scala.util.control.NonFatal
import scala.xml.NodeSeq

// incremental compiler
import sbt.SlashSyntax0.given
import sbt.internal.inc.{
  Analysis,
  AnalyzingCompiler,
  ManagedLoggedReporter,
  MixedAnalyzingCompiler,
  ScalaInstance
}
import sbt.internal.io.Retry
import xsbti.{
  AppConfiguration,
  CompileFailed,
  CrossValue,
  FileConverter,
  HashedVirtualFileRef,
  Position,
  VirtualFile,
  VirtualFileRef
}
import xsbti.compile.{
  AnalysisContents,
  AnalysisStore,
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
  TastyFiles,
  TransactionalManagerType
}
import sbt.internal.IncrementalTest

object Defaults extends BuildCommon {
  final val CacheDirectoryName = "cache"

  def configSrcSub(key: SettingKey[File]): Initialize[File] =
    Def.setting {
      (ThisScope.copy(config = Zero) / key).value / nameForSrc(configuration.value.name)
    }
  def nameForSrc(config: String) = if (config == Configurations.Compile.name) "main" else config
  def prefix(config: String) = if (config == Configurations.Compile.name) "" else config + "-"

  def lock(app: xsbti.AppConfiguration): xsbti.GlobalLock = LibraryManagement.lock(app)

  private[sbt] def globalDefaults(ss: Seq[Setting[?]]): Seq[Setting[?]] =
    Def.defaultSettings(inScope(GlobalScope)(ss))

  lazy val buildCore: Seq[Setting[?]] = thisBuildCore ++ globalCore
  private def thisBuildCore: Seq[Setting[?]] =
    inScope(ThisBuildScope)(
      Seq(
        managedDirectory := baseDirectory.value / "lib_managed"
      )
    )
  private[sbt] lazy val globalCore: Seq[Setting[?]] = globalDefaults(
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

  private[sbt] lazy val globalJvmCore: Seq[Setting[?]] =
    Seq(
      compilerCache := state.value
        .get(Keys.stateCompilerCache)
        .getOrElse(CompilerCache.fresh),
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
      releaseNotesURL := None,
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
      packageTimestamp :== Pkg.defaultTimestamp,
      rootPaths := {
        val app = appConfiguration.value
        val coursierCache = csrCacheDirectory.value.toPath
        val out = rootOutputDirectory.value
        getRootPaths(out, app) + ("CSR_CACHE" -> coursierCache)
      },
      fileConverter := MappedFileConverter(rootPaths.value, allowMachinePath.value)
    ) ++ BuildServerProtocol.globalSettings

  private[sbt] def getRootPaths(out: NioPath, app: AppConfiguration): ListMap[String, NioPath] =
    val base = app.baseDirectory.getCanonicalFile.toPath
    val boot = app.provider.scalaProvider.launcher.bootDirectory.toPath
    val ih = app.provider.scalaProvider.launcher.ivyHome.toPath
    val javaHome = Paths.get(sys.props("java.home"))
    ListMap(
      "OUT" -> out,
      "BASE" -> base,
      "SBT_BOOT" -> boot,
      "IVY_HOME" -> ih,
      "JAVA_HOME" -> javaHome
    )

  private[sbt] lazy val globalIvyCore: Seq[Setting[?]] =
    Seq(
      internalConfigurationMap :== Configurations.internalMap,
      credentials :== SysProp.sbtCredentialsEnv.toList,
      exportJars :== true,
      trackInternalDependencies :== TrackLevel.TrackAlways,
      exportToInternal :== TrackLevel.TrackAlways,
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
      sbtResolvers := Vector(sbtResolver.value),
      crossVersion :== Disabled(),
      buildDependencies := Classpaths.constructBuildDependencies.value,
      version :== "0.1.0-SNAPSHOT",
      versionScheme :== None,
      classpathTypes :== Set(
        "jar",
        "bundle",
        "maven-plugin",
        "test-jar"
      ) ++ CustomPomParser.JarPackagings,
      artifactClassifier :== None,
      checksums := Classpaths.bootChecksums(appConfiguration.value),
      conflictManager := ConflictManager.default,
      pomExtra :== NodeSeq.Empty,
      pomPostProcess :== idFun,
      pomAllRepositories :== false,
      pomIncludeRepository :== Classpaths.defaultRepositoryFilter,
      updateOptions := UpdateOptions(),
      forceUpdatePeriod :== None,
      platform :== Platform.jvm,
      // coursier settings
      csrExtraCredentials :== Nil,
      csrLogger := LMCoursier.coursierLoggerTask.value,
      csrMavenProfiles :== Set.empty,
      csrReconciliations :== LMCoursier.relaxedForAllModules,
      csrSameVersions := Seq(
        ScalaArtifacts.Artifacts.map(a => InclExclRule(scalaOrganization.value, a)).toSet
      ),
      csrCacheDirectory := LMCoursier.defaultCacheLocation,
    )

  /** Core non-plugin settings for sbt builds.  These *must* be on every build or the sbt engine will fail to run at all. */
  private[sbt] lazy val globalSbtCore: Seq[Setting[?]] = globalDefaults(
    Seq(
      outputStrategy :== None, // TODO - This might belong elsewhere.
      buildStructure := Project.structure(state.value),
      settingsData := buildStructure.value.data,
      allScopes := ScopeFilter.allScopes.value,
      checkBuildSources / aggregate :== false,
      checkBuildSources / changedInputFiles / aggregate := false,
      checkBuildSources / Continuous.dynamicInputs := None,
      checkBuildSources / fileInputs := CheckBuildSources.buildSourceFileInputs.value,
      checkBuildSources := CheckBuildSources.needReloadImpl.value,
      fileCacheSize := "128M",
      trapExit :== true,
      connectInput :== false,
      cancelable :== true,
      taskCancelStrategy := { (state: State) =>
        if (cancelable.value) TaskCancellationStrategy.Signal
        else TaskCancellationStrategy.Null
      },
      envVars :== Map.empty,
      sbtVersion := appConfiguration.value.provider.id.version,
      sbtBinaryVersion := binarySbtVersion(sbtVersion.value),
      pluginCrossBuild / sbtVersion := sbtVersion.value,
      onLoad := idFun[State],
      onUnload := idFun[State],
      onUnload := { s =>
        try onUnload.value(s)
        finally IO.delete(taskTemporaryDirectory.value)
      },
      // // extraLoggers is deprecated
      SettingKey[ScopedKey[?] => Seq[XAppender]]("extraLoggers") :== { _ =>
        Nil
      },
      extraAppenders := {
        val f = SettingKey[ScopedKey[?] => Seq[XAppender]]("extraLoggers").value
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
      commandProgress := Seq(),
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
      templateDescriptions :== TemplateCommandUtil.defaultTemplateDescriptions,
      templateRunLocal := templateRunLocalInputTask(runLocalTemplate).evaluated,
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

  private[sbt] lazy val buildLevelJvmSettings: Seq[Setting[?]] = Seq(
    exportPipelining := usePipelining.value,
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

  def defaultTestTasks(key: Scoped): Seq[Setting[?]] =
    inTask(key)(
      Seq(
        tags := Seq(Tags.Test -> 1),
        logBuffered := true
      )
    )

  // TODO: This should be on the new default settings for a project.
  def projectCore: Seq[Setting[?]] = Seq(
    name := thisProject.value.id,
    logManager := LogManager.defaults(extraAppenders.value, ConsoleOut.terminalOut),
    onLoadMessage := (onLoadMessage or
      Def.setting {
        s"set current project to ${name.value} (in build ${thisProjectRef.value.build})"
      }).value
  )

  // Appended to JvmPlugin.projectSettings
  def paths: Seq[Setting[?]] = Seq(
    baseDirectory := thisProject.value.base,
    target := rootOutputDirectory.value.resolve(outputPath.value).toFile(),
    // Use a different history path for jline3 because the jline2 format is
    // incompatible. By sbt 1.4.0, we should consider revering this to t / ".history"
    // and possibly rewriting the jline2 history in a jline3 compatible format if the
    // history file is incompatible. For now, just use a different file to facilitate
    // going back and forth between 1.3.x and 1.4.x.
    historyPath := (historyPath or target(t => Option(t / ".history3"))).value,
    sourceDirectory := baseDirectory.value / "src",
    sourceManaged := target.value / "src_managed",
    resourceManaged := target.value / "resource_managed",
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
    resourceGenerators += (Def.task {
      PluginDiscovery.writeDescriptors(discoveredSbtPlugins.value, resourceManaged.value)
    }).taskValue,
    managedResources := generate(resourceGenerators).value,
    resources := Classpaths.concat(managedResources, unmanagedResources).value,
    resourceDigests := {
      val uifs = (unmanagedResources / inputFileStamps).value
      val mifs = (managedResources / inputFileStamps).value
      (uifs ++ mifs).sortBy(_._1.toString()).map { case (p, fileStamp) =>
        FileStamp.toDigest(p, fileStamp)
      }
    },
  )
  lazy val outputConfigPaths: Seq[Setting[?]] = Seq(
    classDirectory := target.value / (prefix(configuration.value.name) + "classes"),
    backendOutput := {
      val converter = fileConverter.value
      val dir = target.value / (prefix(configuration.value.name) + "backend")
      converter.toVirtualFile(dir.toPath)
    },
    earlyOutput / artifactPath := configArtifactPathSetting(artifact, "early").value,
    earlyOutput := {
      (earlyOutput / artifactPath).value match
        case vf: VirtualFile => vf
    },
    semanticdbTargetRoot := target.value / (prefix(configuration.value.name) + "meta"),
    compileAnalysisTargetRoot := target.value / (prefix(configuration.value.name) + "zinc"),
    earlyCompileAnalysisTargetRoot := target.value / (prefix(
      configuration.value.name
    ) + "early-zinc"),
    doc / target := target.value / (prefix(configuration.value.name) + "api")
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
        override protected def loadClass(name: String, resolve: Boolean): Class[?] = {
          if (name.startsWith("org.fusesource")) throw new ClassNotFoundException(name)
          super.loadClass(name, resolve)
        }
      }
      else topLoader
    },
    scalaInstance := Compiler.scalaInstanceTask.value,
    crossVersion := (if (crossPaths.value) CrossVersion.binary else CrossVersion.disabled),
    pluginCrossBuild / sbtBinaryVersion := binarySbtVersion(
      (pluginCrossBuild / sbtVersion).value
    ),
    // Use (pluginCrossBuild / sbtVersion) to pick the sbt module to depend from the plugin.
    // Because `pluginCrossBuild / sbtVersion` can be scoped to project level,
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
      val base = ModuleID(id.groupID, id.name, sv).withCrossVersion(cross).platform(Platform.jvm)
      CrossVersion(scalaV, binVersion)(base).withCrossVersion(Disabled())
    },
    crossSbtVersions := Vector((pluginCrossBuild / sbtVersion).value),
    crossTarget := target.value,
    scalaCompilerBridgeBinaryJar := {
      val sv = scalaVersion.value
      val managed = managedScalaInstance.value
      val hasSbtBridge = ScalaArtifacts.isScala3(sv) || ZincLmUtil.hasScala2SbtBridge(sv)
      if hasSbtBridge && managed then
        val jar = ZincLmUtil.fetchDefaultBridgeModule(
          sv,
          dependencyResolution.value,
          updateConfiguration.value,
          (update / unresolvedWarningConfiguration).value,
          streams.value.log
        )
        Some(jar)
      else None
    },
    scalaCompilerBridgeSource := ZincLmUtil.getDefaultBridgeSourceModule(scalaVersion.value),
    auxiliaryClassFiles ++= {
      if (ScalaArtifacts.isScala3(scalaVersion.value)) List(TastyFiles.instance)
      else Nil
    },
    consoleProject / scalaCompilerBridgeBinaryJar := None,
    consoleProject / scalaCompilerBridgeSource := ZincLmUtil.getDefaultBridgeSourceModule(
      appConfiguration.value.provider.scalaProvider.version
    ),
    classpathOptions := ClasspathOptionsUtil.noboot(scalaVersion.value),
    console / classpathOptions := ClasspathOptionsUtil.replNoboot(scalaVersion.value),
  )
  private lazy val compileBaseGlobal: Seq[Setting[?]] = globalDefaults(
    Seq(
      auxiliaryClassFiles :== Nil,
      incOptions := IncOptions.of(),
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

  lazy val configTasks: Seq[Setting[?]] = docTaskSettings(doc) ++
    inTask(compile)(compileInputsSettings) ++
    inTask(compileJava)(
      Seq(
        compileInputs := {
          val opts = (compileJava / compileOptions).value
          (compile / compileInputs).value.withOptions(opts)
        },
        compileOptions := {
          val opts = (compile / compileOptions).value
          val cp0 = dependencyClasspath.value
          val cp1 = backendOutput.value +: data(cp0)
          val converter = fileConverter.value
          val cp = cp1.map(converter.toPath).map(converter.toVirtualFile)
          opts.withClasspath(cp.toArray)
        }
      )
    ) ++
    configGlobal ++ compileAnalysisSettings ++ Seq(
      compileOutputs := {
        import scala.jdk.CollectionConverters.*
        val c = fileConverter.value
        val (_, vfDir, packedDir) = compileIncremental.value
        val classFiles = compile.value.readStamps.getAllProductStamps.keySet.asScala
        classFiles.toSeq.map(c.toPath) :+
          compileAnalysisFile.value.toPath :+
          c.toPath(vfDir) :+
          c.toPath(packedDir)
      },
      compileOutputs := compileOutputs.triggeredBy(compile).value,
      tastyFiles := Def.taskIf {
        if (ScalaArtifacts.isScala3(scalaVersion.value)) {
          val _ = compile.value
          val c = fileConverter.value
          val dir = c.toPath(backendOutput.value).toFile
          val tastyFiles = dir.**("*.tasty").get()
          tastyFiles.map(_.getAbsoluteFile)
        } else Nil
      }.value,
      clean := {
        (compileOutputs / clean).value
        (products / clean).value
      },
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
            promise.complete(Result.Value(isSuccess))
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
      zincCompilationListeners := Seq.empty,
      incOptions := {
        val old = incOptions.value
        val extHooks = externalHooks.value
        val newExtHooks = extHooks.withInvalidationProfiler(() =>
          new DefaultRunProfiler(zincCompilationListeners.value)
        )
        old
          .withAuxiliaryClassFiles(auxiliaryClassFiles.value.toArray)
          .withExternalHooks(newExtHooks)
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
          Vector(
            "-Ypickle-java",
            "-Ypickle-write",
            converter.toPath(earlyOutput.value).toString
          ) ++ old
        else old
      },
      scalacOptions := {
        val old = scalacOptions.value
        if (
          sbtPlugin.value && VersionNumber(scalaVersion.value)
            .matchesSemVer(SemanticSelector("=2.12 >=2.12.13"))
        )
          old ++ Seq("-Wconf:cat=unused-nowarn:s", "-Xsource:3")
        else old
      },
      persistJarClasspath :== true,
      classpathEntryDefinesClassVF := {
        val cache =
          if persistJarClasspath.value then classpathDefinesClassCache.value
          else VirtualFileValueCache.definesClassCache(fileConverter.value)
        cache.get
      },
      compileIncSetup := compileIncSetupTask.value,
      console := consoleTask.value,
      collectAnalyses := Definition.collectAnalysesTask.map(_ => ()).value,
      consoleQuick := consoleQuickTask.value,
      discoveredMainClasses := compile
        .map(discoverMainClasses)
        .storeAs(discoveredMainClasses)
        .triggeredBy(compile)
        .value,
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
      runBlock := {
        val r = run.evaluated
        val service = bgJobService.value
        service.waitForTry(r.handle).get
        r
      },
      fgRun := runTask(fullClasspath, (run / mainClass), (run / runner)).evaluated,
      fgRunMain := runMainTask(fullClasspath, (run / runner)).evaluated,
      copyResources := copyResourcesTask.value,
      // note that we use the same runner and mainClass as plain run
      mainBgRunMainTaskForConfig(This),
      mainBgRunTaskForConfig(This)
    ) ++ inTask(run)(runnerSettings ++ newRunnerSettings) ++ compileIncrementalTaskSettings

  private lazy val configGlobal = globalDefaults(
    Seq(
      initialCommands :== "",
      cleanupCommands :== "",
      asciiGraphWidth :== 80
    )
  )

  lazy val projectTasks: Seq[Setting[?]] = Seq(
    cleanFiles := cleanFilesTask.value,
    cleanKeepFiles := Vector.empty,
    cleanKeepGlobs ++= historyPath.value.map(_.toGlob).toVector,
    // clean := Def.taskDyn(Clean.task(resolvedScoped.value.scope, full = true)).value,
    clean := Clean.scopedTask.value,
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
    import ScopeFilter.Make.*
    val selectDeps = ScopeFilter(inAggregates(ThisProject) || inDependencies(ThisProject))
    watchSources.??(Nil).all(selectDeps).map(_.flatten)

  def transitiveUpdateTask: Initialize[Task[Seq[UpdateReport]]] = {
    import ScopeFilter.Make.*
    val selectDeps = ScopeFilter(inDependencies(ThisProject, includeRoot = false))
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
      val msg = watchingMessage.getOrElse(Watched.defaultWatchingMessage).value
      val trigMsg = triggeredMessage.getOrElse(Watched.defaultTriggeredMessage).value
      new Watched {
        val scoped = (base / watchTransitiveSources)
        val key = scoped.scopedKey
        override def antiEntropy: FiniteDuration = _antiEntropy
        override def pollInterval: FiniteDuration = interval
        override def watchingMessage(s: WatchState) = msg(s)
        override def triggeredMessage(s: WatchState) = trigMsg(s)
        override def watchService() = getService()
        override def watchSources(s: State) =
          EvaluateTask(Project.structure(s), key, s, base) match
            case Some((_, Result.Value(ps))) => ps
            case Some((_, Result.Inc(i)))    => throw i
            case None                        => sys.error("key not found: " + Def.displayFull(key))
      }
    }

  // Returns the ScalaInstance only if it was not constructed via `update`
  //  This is necessary to prevent cycles between `update` and `scalaInstance`
  private[sbt] def unmanagedScalaInstanceOnly: Initialize[Task[Option[ScalaInstance]]] =
    (Def.task { scalaHome.value }).flatMapTask { case h =>
      if h.isDefined then Def.task(Some(scalaInstance.value))
      else Def.task(None)
    }

  private def testDefaults =
    Defaults.globalDefaults(
      Seq(
        testFrameworks :== sbt.TestFrameworks.All,
        testListeners :== Nil,
        testOptions :== Nil,
        testOptionDigests := Nil,
        testResultLogger :== TestResultLogger.Default,
        testOnly / testFilter :== (IncrementalTest.selectedFilter),
        extraTestDigests :== Nil,
      )
    )
  lazy val testTasks: Seq[Setting[?]] = Def.settings(
    testTaskOptions(test),
    testTaskOptions(testOnly),
    testTaskOptions(testQuick),
    testDefaults,
    testLoader := ClassLoaders.testTask.value,
    loadedTestFrameworks := {
      val loader = testLoader.value
      val log = streams.value.log
      testFrameworks.value.flatMap(f => f.create(loader, log).map(x => (f, x))).toMap
    },
    definedTests := detectTests.value,
    definedTestNames := definedTests
      .map(_.map(_.name).distinct)
      .storeAs(definedTestNames)
      .triggeredBy(compile)
      .value,
    definedTestDigests := IncrementalTest.definedTestDigestTask
      .triggeredBy(compile)
      .value,
    testQuick / testFilter := IncrementalTest.filterTask.value,
    extraTestDigests ++= IncrementalTest.extraTestDigestsTask.value,
    executeTests := {
      import sbt.TupleSyntax.*
      (
        test / streams,
        loadedTestFrameworks,
        testLoader,
        (test / testGrouping),
        (test / testExecution),
        (test / fullClasspath),
        testForkedParallel,
        (test / javaOptions),
        (classLoaderLayeringStrategy),
        thisProject,
        fileConverter,
      ).flatMapN { case (s, lt, tl, gp, ex, cp, fp, jo, clls, thisProj, c) =>
        allTestGroupsTask(
          s,
          lt,
          tl,
          gp,
          ex,
          cp,
          fp,
          jo,
          clls,
          projectId = s"${thisProj.id} / ",
          c,
        )
      }
    }.value,
    // ((streams in test, loadedTestFrameworks, testLoader, testGrouping in test, testExecution in test, fullClasspath in test, javaHome in test, testForkedParallel, javaOptions in test) flatMap allTestGroupsTask).value,
    Test / testFull / testResultLogger :== TestResultLogger.SilentWhenNoTests, // https://github.com/sbt/sbt/issues/1185
    testFull := {
      val trl = (Test / testFull / testResultLogger).value
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
    },
    test := testQuick.evaluated,
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

  /**
   * A scope whose configuration axis is set to Zero.
   */
  lazy val ConfigZero: Scope = ThisScope.copy(config = Zero)
  def testTaskOptions(key: Scoped): Seq[Setting[?]] =
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
            TestStatusReporter(
              definedTestDigests.value,
              Def.cacheConfiguration.value,
            ) +:
            (TaskZero / testListeners).value
        },
        testOptions := Tests.Listeners(testListeners.value) +: (TaskZero / testOptions).value,
        testOptionDigests := {
          (TaskZero / testOptions).value.flatMap {
            case Tests.Setup(_, digest)   => Seq(digest)
            case Tests.Cleanup(_, digest) => Seq(digest)
            case Tests.Argument(fm, args) =>
              Seq(
                Digest.sha256Hash(
                  (fm.toSeq.map(_.toString) ++ args).mkString("\n").getBytes("UTF-8")
                )
              )
            case _ => Nil
          }
        },
        testExecution := testExecutionTask(key).value
      )
    ) ++ inScope(GlobalScope)(
      Seq(
        derive(testGrouping := singleTestGroupDefault.value)
      )
    )

  private def closeableTestLogger(manager: Streams, baseKey: Scoped, buffered: Boolean)(
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
  lazy val singleTestGroupDefault: Initialize[Task[Seq[Tests.Group]]] = Def.task {
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

  @nowarn
  def inputTests(key: InputKey[?]): Initialize[InputTask[Unit]] =
    inputTests0.mapReferenced(Def.mapScope((s) => s.rescope(key.key)))

  private lazy val inputTests0: Initialize[InputTask[Unit]] = {
    val parser = loadForParser(definedTestNames)((s, i) => testOnlyParser(s, i getOrElse Nil))
    ParserGen(parser).flatMapTask { case ((selected, frameworkOptions)) =>
      val s = streams.value
      val filter = testFilter.value
      val config = testExecution.value
      val st = state.value
      given display: Show[ScopedKey[?]] = Project.showContextKey(st)
      val modifiedOpts =
        Tests.Filters(filter(selected)) +: Tests.Argument(frameworkOptions*) +: config.options
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
        converter = fileConverter.value,
      )
      val taskName = display.show(resolvedScoped.value)
      val trl = testResultLogger.value
      (Def
        .value[Task[Tests.Output]] { output })
        .map { out => trl.run(s.log, out, taskName) }
    }
  }

  def createTestRunners(
      frameworks: Map[TestFramework, Framework],
      loader: ClassLoader,
      config: Tests.Execution
  ): Map[TestFramework, Runner] = {
    import Tests.Argument
    val opts = config.options.toList
    frameworks.map { case (tf, f) =>
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
      converter: FileConverter,
  ): Task[Tests.Output] = {
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
      converter = converter,
    )
  }

  private[sbt] def allTestGroupsTask(
      s: TaskStreams,
      frameworks: Map[TestFramework, Framework],
      loader: ClassLoader,
      groups: Seq[Tests.Group],
      config: Tests.Execution,
      cp: Classpath,
      converter: FileConverter,
      forkedParallelExecution: Boolean,
  ): Task[Tests.Output] = {
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
      converter = converter,
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
      projectId: String,
      converter: FileConverter,
  ): Task[Tests.Output] = {
    val processedOptions: Map[Tests.Group, Tests.ProcessedOptions] =
      groups
        .map(group => group -> Tests.processOptions(config, group.tests.toVector, s.log))
        .toMap

    val testDefinitions: Iterable[TestDefinition] = processedOptions.values.flatMap(_.tests)

    val filteredFrameworks: Map[TestFramework, Framework] = frameworks.filter {
      case (_, framework) =>
        TestFramework.getFingerprints(framework).exists { t =>
          testDefinitions.exists { (test) =>
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
            data(cp),
            converter,
            opts,
            s.log,
            (Tags.ForkedTestGroup, 1) +: group.tags*
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
      out.events.foreach { case (suite, e) =>
        if (
          strategy != ClassLoaderLayeringStrategy.Flat ||
          strategy != ClassLoaderLayeringStrategy.ScalaLibrary
        ) {
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
        runners map { case (tf, r) =>
          Tests.Summary(frameworks(tf).name, r.done())
        }
      out.copy(summaries = summaries)
    }
    // Def.value[Task[Tests.Output]] {
    result
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

  lazy val packageBase: Seq[Setting[?]] = Seq(
    artifact := Artifact(moduleName.value)
  ) ++ Defaults.globalDefaults(
    Seq(
      packageOptions :== Nil,
      artifactName :== (Artifact.artifactName)
    )
  )

  lazy val packageConfig: Seq[Setting[?]] =
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

          Pkg.addSpecManifestAttributes(n, ver, orgName) +:
            Pkg.addImplManifestAttributes(n, ver, homepage.value, org, orgName) +:
            Pkg.setFixedTimestamp(ts) +:
            main.map(Pkg.MainClass.apply) ++: old
        }
      )
    ) ++
      inTask(packageSrc)(
        Seq(
          packageOptions := {
            val old = packageOptions.value
            val ts = packageTimestamp.value
            Pkg.addSpecManifestAttributes(
              name.value,
              version.value,
              organizationName.value
            ) +: Pkg.setFixedTimestamp(ts) +: old
          }
        )
      ) ++
      packageTaskSettings(packageBin, packageBinMappings) ++
      packageTaskSettings(packageSrc, packageSrcMappings) ++
      packageTaskSettings(packageDoc, packageDocMappings) ++
      Seq(Keys.`package` := packageBin.value)

  def packageBinMappings: Initialize[Task[Seq[(HashedVirtualFileRef, String)]]] =
    Def.task {
      val converter = fileConverter.value
      val xs = products.value
      xs
        .flatMap(Path.allSubpaths)
        .filter(_._1.isFile())
        .map { case (p, path) =>
          val vf = converter.toVirtualFile(p.toPath())
          (vf: HashedVirtualFileRef) -> path
        }
    }

  def packageDocMappings: Initialize[Task[Seq[(HashedVirtualFileRef, String)]]] =
    Def.task {
      val converter = fileConverter.value
      val d = doc.value
      Path
        .allSubpaths(d)
        .toSeq
        .filter(_._1.isFile())
        .map { case (p, path) =>
          val vf = converter.toVirtualFile(p.toPath())
          (vf: HashedVirtualFileRef) -> path
        }
    }

  def packageSrcMappings: Initialize[Task[Seq[(HashedVirtualFileRef, String)]]] =
    concatMappings(resourceMappings, sourceMappings)

  private type Mappings = Initialize[Task[Seq[(HashedVirtualFileRef, String)]]]
  def concatMappings(as: Mappings, bs: Mappings): Mappings =
    as.zipWith(bs) {
      (
          a: Task[Seq[(HashedVirtualFileRef, String)]],
          b: Task[Seq[(HashedVirtualFileRef, String)]]
      ) =>
        (a, b).mapN {
          case (
                seq1: Seq[(HashedVirtualFileRef, String)],
                seq2: Seq[(HashedVirtualFileRef, String)]
              ) =>
            seq1 ++ seq2
        }
    }

  // drop base directories, since there are no valid mappings for these
  def sourceMappings: Initialize[Task[Seq[(HashedVirtualFileRef, String)]]] =
    Def.task {
      val converter = fileConverter.value
      val sdirs = sourceDirectories.value
      val base = baseDirectory.value
      val relative = (f: File) => relativeTo(sdirs)(f).orElse(relativeTo(base)(f)).orElse(flat(f))
      val exclude = Set(sdirs, base)
      sources.value
        .flatMap {
          case s if !exclude(s) => relative(s).map(s -> _)
          case _                => None
        }
        .map { case (p, path) =>
          val vf = converter.toVirtualFile(p.toPath())
          (vf: HashedVirtualFileRef) -> path
        }
    }

  def resourceMappings: Initialize[Task[Seq[(HashedVirtualFileRef, String)]]] =
    relativeMappings(resources, resourceDirectories)

  def relativeMappings(
      files: Taskable[Seq[File]],
      dirs: Taskable[Seq[File]]
  ): Initialize[Task[Seq[(HashedVirtualFileRef, String)]]] =
    Def.task {
      val converter = fileConverter.value
      val rdirs = dirs.toTask.value.toSet
      val relative = (f: File) => relativeTo(rdirs)(f).orElse(flat(f))
      files.toTask.value
        .flatMap {
          case r if !rdirs(r) => relative(r).map(r -> _)
          case _              => None
        }
        .map { case (p, path) =>
          val vf = converter.toVirtualFile(p.toPath())
          (vf: HashedVirtualFileRef) -> path
        }
    }

  def collectFiles(
      dirs: Taskable[Seq[File]],
      filter: Taskable[FileFilter],
      excludes: Taskable[FileFilter]
  ): Initialize[Task[Seq[File]]] =
    Def.task {
      dirs.toTask.value.descendantsExcept(filter.toTask.value, excludes.toTask.value).get()
    }

  def relativeMappings( // forward to widened variant
      files: ScopedTaskable[Seq[File]],
      dirs: ScopedTaskable[Seq[File]]
  ): Initialize[Task[Seq[(HashedVirtualFileRef, String)]]] =
    relativeMappings(files: Taskable[Seq[File]], dirs)

  def collectFiles( // forward to widened variant
      dirs: ScopedTaskable[Seq[File]],
      filter: ScopedTaskable[FileFilter],
      excludes: ScopedTaskable[FileFilter]
  ): Initialize[Task[Seq[File]]] = collectFiles(dirs: Taskable[Seq[File]], filter, excludes)

  private[sbt] def configArtifactPathSetting(
      art: SettingKey[Artifact],
      extraPrefix: String
  ): Initialize[VirtualFile] =
    Def.setting {
      val f = artifactName.value
      val converter = fileConverter.value
      val p = target.value /
        (prefix(configuration.value.name) + extraPrefix) / f(
          ScalaVersion(
            (artifactName / scalaVersion).value,
            (artifactName / scalaBinaryVersion).value
          ),
          projectID.value,
          art.value
        )
      converter.toVirtualFile(p.toPath())
    }

  private[sbt] def prefixArtifactPathSetting(
      art: SettingKey[Artifact],
      extraPrefix: String
  ): Initialize[VirtualFileRef] =
    Def.setting {
      val f = artifactName.value
      val converter = fileConverter.value
      val p = target.value / extraPrefix / f(
        ScalaVersion(
          (artifactName / scalaVersion).value,
          (artifactName / scalaBinaryVersion).value
        ),
        projectID.value,
        art.value
      )
      converter.toVirtualFile(p.toPath())
    }

  def artifactPathSetting(art: SettingKey[Artifact]): Initialize[VirtualFileRef] =
    Def.setting {
      val f = artifactName.value
      val p = target.value / f(
        ScalaVersion(
          (artifactName / scalaVersion).value,
          (artifactName / scalaBinaryVersion).value
        ),
        projectID.value,
        art.value
      )
      val converter = fileConverter.value
      converter.toVirtualFile(p.toPath())
    }

  lazy val artifactSetting: Initialize[Artifact] =
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

  def packageTaskSettings(
      key: TaskKey[HashedVirtualFileRef],
      mappingsTask: Initialize[Task[Seq[(HashedVirtualFileRef, String)]]]
  ) =
    inTask(key)(
      Seq(
        (TaskZero / key) := packageTask.value,
        packageConfiguration := packageConfigurationTask.value,
        mappings := mappingsTask.value,
        packagedArtifact := artifact.value -> key.value,
        artifact := artifactSetting.value,
        artifactPath := artifactPathSetting(artifact).value
      )
    )

  lazy val packageTask: Initialize[Task[HashedVirtualFileRef]] =
    Def.cachedTask {
      val config = packageConfiguration.value
      val s = streams.value
      val converter = fileConverter.value
      val out = Pkg(
        config,
        converter,
        s.log,
        Pkg.timeFromConfiguration(config)
      )
      s.log.debug(s"wrote $out")
      Def.declareOutput(out)
      out
    }

  lazy val packageConfigurationTask: Initialize[Task[Pkg.Configuration]] =
    Def.task {
      Pkg.Configuration(
        mappings.value,
        artifactPath.value,
        packageOptions.value,
      )
    }

  def askForMainClass(classes: Seq[String]): Option[String] =
    sbt.SelectMainClass(
      if (classes.length >= 10) Some(SimpleReader(ITerminal.get).readLine(_))
      else
        Some(s => {
          def print(st: String) = { scala.Console.out.print(st); scala.Console.out.flush() }
          print(s)
          ITerminal.get.withRawInput {
            try
              ITerminal.get.inputStream.read match {
                case -1 | -2 => None
                case b =>
                  val res = b.toChar.toString
                  println(res)
                  Some(res)
              }
            catch { case e: InterruptedException => None }
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

  private def termWrapper(canonical: Boolean, echo: Boolean): (() => Unit) => (() => Unit) =
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
    val parser = Defaults.loadForParser(discoveredMainClasses)((s, names) =>
      Defaults.runMainParser(s, names getOrElse Nil)
    )
    Def.inputTask {
      val service = bgJobService.value
      val (mainClass, args) = parser.parsed
      val hashClasspath = (bgRunMain / bgHashClasspath).value
      val wrapper = termWrapper(canonicalInput.value, echoInput.value)
      val converter = fileConverter.value
      service.runInBackgroundWithLoader(resolvedScoped.value, state.value) { (logger, workingDir) =>
        val cp =
          if copyClasspath.value then
            service.copyClasspath(
              products.value,
              classpath.value,
              workingDir,
              hashClasspath,
              converter,
            )
          else classpath.value
        given FileConverter = fileConverter.value
        scalaRun.value match
          case r: Run =>
            val loader = r.newLoader(cp.files)
            (
              Some(loader),
              wrapper(() => r.runWithLoader(loader, cp.files, mainClass, args, logger).get)
            )
          case sr =>
            (None, wrapper(() => sr.run(mainClass, cp.files, args, logger).get))
      }
    }
  }

  def bgRunTask(
      products: Initialize[Task[Classpath]],
      classpath: Initialize[Task[Classpath]],
      mainClassTask: Initialize[Task[Option[String]]],
      copyClasspath: Initialize[Boolean],
      scalaRun: Initialize[Task[ScalaRun]]
  ): Initialize[InputTask[JobHandle]] =
    val parser = Def.spaceDelimited()
    Def.inputTask {
      val args = parser.parsed
      val service = bgJobService.value
      val mainClass = mainClassTask.value getOrElse sys.error("No main class detected.")
      val hashClasspath = (bgRun / bgHashClasspath).value
      val wrapper = termWrapper(canonicalInput.value, echoInput.value)
      val converter = fileConverter.value
      service.runInBackgroundWithLoader(resolvedScoped.value, state.value) { (logger, workingDir) =>
        val cp =
          if copyClasspath.value then
            service.copyClasspath(
              products.value,
              classpath.value,
              workingDir,
              hashClasspath,
              converter
            )
          else classpath.value
        given FileConverter = converter
        scalaRun.value match
          case r: Run =>
            val loader = r.newLoader(cp.files)
            (
              Some(loader),
              wrapper(() => r.runWithLoader(loader, cp.files, mainClass, args, logger).get)
            )
          case sr =>
            (None, wrapper(() => sr.run(mainClass, cp.files, args, logger).get))
      }
    }

  // `runMain` calls bgRunMain in the background and pauses the current channel
  def foregroundRunMainTask: Initialize[InputTask[EmulateForeground]] =
    Def.inputTask {
      val handle = bgRunMain.evaluated
      EmulateForeground(handle)
    }

  // `run` task calls bgRun in the background and pauses the current channel
  def foregroundRunTask: Initialize[InputTask[EmulateForeground]] =
    Def.inputTask {
      val handle = bgRun.evaluated
      EmulateForeground(handle)
    }

  def runMainTask(
      classpath: Initialize[Task[Classpath]],
      scalaRun: Initialize[Task[ScalaRun]]
  ): Initialize[InputTask[Unit]] = {
    val parser =
      loadForParser(discoveredMainClasses)((s, names) => runMainParser(s, names getOrElse Nil))
    Def.inputTask {
      val (mainClass, args) = parser.parsed
      val cp = classpath.value
      given FileConverter = fileConverter.value
      scalaRun.value
        .run(mainClass, cp.files, args, streams.value.log)
        .get
    }
  }

  def runTask(
      classpath: Initialize[Task[Classpath]],
      mainClassTask: Initialize[Task[Option[String]]],
      scalaRun: Initialize[Task[ScalaRun]]
  ): Initialize[InputTask[Unit]] =
    val parser = Def.spaceDelimited()
    Def.inputTask {
      val in = parser.parsed
      val mainClass = mainClassTask.value getOrElse sys.error("No main class detected.")
      val cp = classpath.value
      given FileConverter = fileConverter.value
      scalaRun.value.run(mainClass, cp.files, in, streams.value.log).get
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

  def docTaskSettings(key: TaskKey[File] = doc): Seq[Setting[?]] =
    inTask(key)(
      Seq(
        apiMappings ++= {
          val dependencyCp = dependencyClasspath.value
          val log = streams.value.log
          if (autoAPIMappings.value) APIMappings.extract(dependencyCp, log).toMap
          else Map.empty[HashedVirtualFileRef, URL]
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
              val xapisFiles = xapis.map { case (k, v) =>
                converter.toPath(k).toFile() -> v
              }
              val options = sOpts ++ Opts.doc.externalAPI(xapisFiles)
              val scalac = cs.scalac match
                case ac: AnalyzingCompiler => ac.onArgs(exported(s, "scaladoc"))
              val docSrcFiles = if ScalaArtifacts.isScala3(sv) then tFiles else srcs
              // todo: cache this
              if docSrcFiles.nonEmpty then
                IO.delete(out)
                IO.createDirectory(out)
                // use PlainVirtualFile since Scaladoc currently doesn't handle actual VirtualFiles
                scalac.doc(
                  docSrcFiles.map(_.toPath()).map(new sbt.internal.inc.PlainVirtualFile(_)),
                  cp.map(converter.toPath).map(new sbt.internal.inc.PlainVirtualFile(_)),
                  converter,
                  out.toPath(),
                  options,
                  maxErrors.value,
                  s.log,
                )
              else ()
            case (_, true) =>
              import sbt.internal.inc.javac.JavaCompilerArguments
              val javaSourcesOnly: VirtualFile => Boolean = _.id.endsWith(".java")
              val classpath = cp.map(converter.toPath).map(converter.toVirtualFile)
              val options = javacOptions.value.toList
              cs.javaTools.javadoc.run(
                srcs.toArray
                  .map { x =>
                    converter.toVirtualFile(x.toPath)
                  }
                  .filter(javaSourcesOnly),
                JavaCompilerArguments(Nil, classpath, options).toArray,
                CompileOutput(out.toPath),
                IncToolOptionsUtil.defaultIncToolOptions(),
                reporter,
                s.log,
              )
            case _ => () // do nothing
          }
          out
        }
      )
    )

  def mainBgRunTask = mainBgRunTaskForConfig(Select(Runtime))
  def mainBgRunMainTask = mainBgRunMainTaskForConfig(Select(Runtime))

  private def mainBgRunTaskForConfig(c: ScopeAxis[ConfigKey]) =
    bgRun := bgRunTask(
      exportedProductJars,
      This / c / This / fullClasspathAsJars,
      run / mainClass,
      bgRun / bgCopyClasspath,
      run / runner
    ).evaluated

  private def mainBgRunMainTaskForConfig(c: ScopeAxis[ConfigKey]) =
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
      ConsoleProject(state.value, (consoleProject / initialCommands).value)(using streams.value.log)
      println()
    }

  def consoleTask: Initialize[Task[Unit]] = consoleTask(fullClasspath, console)
  def consoleQuickTask = consoleTask(externalDependencyClasspath, consoleQuick)
  def consoleTask(classpath: TaskKey[Classpath], task: TaskKey[?]): Initialize[Task[Unit]] =
    Def.task {
      val si = (task / scalaInstance).value
      val s = streams.value
      val cp = data((task / classpath).value)
      val converter = fileConverter.value
      val cpFiles = cp.map(converter.toPath).map(_.toFile())
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
      (new Console(compiler))(cpFiles, sc, loader, ic, cc)()(using s.log).get
      println()
    }

  private def exported(w: PrintWriter, command: String): Seq[String] => Unit =
    args => w.println((command +: args).mkString(" "))

  private def exported(s: TaskStreams, command: String): Seq[String] => Unit = {
    val w = s.text(ExportStream)
    try exported(w, command)
    finally w.close() // workaround for #937
  }

  /**
   * Handles traditional Scalac compilation. For non-pipelined compilation,
   *  this also handles Java compilation.
   */
  private[sbt] def compileScalaBackendTask: Initialize[Task[CompileResult]] = Def.task {
    val setup: Setup = compileIncSetup.value
    val _ = compileIncremental.value
    val exportP = exportPipelining.value
    // Save analysis midway if pipelining is enabled
    val store = analysisStore(compileAnalysisFile)
    val contents = store.unsafeGet()
    if (exportP) {
      // this stores the eary analysis (again) in case the subproject contains a macro
      setup.earlyAnalysisStore.toOption map { earlyStore =>
        earlyStore.set(contents)
      }
    }
    CompileResult.of(
      contents.getAnalysis(),
      contents.getMiniSetup(),
      contents.getAnalysis().readCompilations().getAllCompilations().nonEmpty
    )
  }

  /**
   * Block on earlyOutputPing promise, which will be completed by `compile` midway
   * via `compileProgress` implementation.
   */
  private[sbt] def compileEarlyTask: Initialize[Task[CompileAnalysis]] = Def.task {
    if ({
      streams.value.log
        .debug(s"${name.value}: compileEarly: blocking on earlyOutputPing")
      earlyOutputPing.await.value
    }) {
      val store = analysisStore(earlyCompileAnalysisFile)
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
    val store = analysisStore(compileAnalysisFile)
    val c = fileConverter.value
    // TODO - expose bytecode manipulation phase.
    val analysisResult: CompileResult = manipulateBytecode.value
    if (analysisResult.hasModified) {
      val contents = AnalysisContents.create(analysisResult.analysis(), analysisResult.setup())
      store.set(contents)
    }
    val map = managedFileStampCache.value
    val analysis = analysisResult.analysis
    import scala.jdk.CollectionConverters.*
    analysis.readStamps.getAllProductStamps.asScala.foreach { case (f: VirtualFileRef, s) =>
      map.put(c.toPath(f), sbt.nio.FileStamp.fromZincStamp(s))
    }
    analysis
  }

  def compileIncrementalTaskSettings = inTask(compileIncremental)(
    Seq(
      (TaskZero / compileIncremental) := {
        val bspTask = (compile / bspCompileTask).value
        val result = cachedCompileIncrementalTask.result.value
        val reporter = (compile / bspReporter).value
        val store = analysisStore(compileAnalysisFile)
        val ci = (compile / compileInputs).value
        val c = fileConverter.value
        val dir = c.toPath(backendOutput.value).toFile
        result match
          case Result.Value(res) =>
            val analysis = store.unsafeGet().getAnalysis()
            reporter.sendSuccessReport(analysis)
            bspTask.notifySuccess(analysis)
            res
          case Result.Inc(cause) =>
            val compileFailed = cause.directCause.collect { case c: CompileFailed => c }
            reporter.sendFailureReport(ci.options.sources)
            bspTask.notifyFailure(compileFailed)
            throw cause
      },
    )
  )

  private val cachedCompileIncrementalTask = Def
    .cachedTask {
      val s = streams.value
      val ci = (compile / compileInputs).value
      val bspTask = (compile / bspCompileTask).value
      // This is a cacheable version
      val ci2 = (compile / compileInputs2).value
      val ping = (TaskZero / earlyOutputPing).value
      val setup: Setup = (TaskZero / compileIncSetup).value
      val store = analysisStore(compileAnalysisFile)
      val c = fileConverter.value
      // TODO - Should readAnalysis + saveAnalysis be scoped by the compile task too?
      val analysisResult = Retry(compileIncrementalTaskImpl(bspTask, s, ci, ping))
      val analysisOut = c.toVirtualFile(setup.cachePath())
      val contents = AnalysisContents.create(analysisResult.analysis(), analysisResult.setup())
      store.set(contents)
      Def.declareOutput(analysisOut)
      val dir = ci.options.classesDirectory
      val vfDir = c.toVirtualFile(dir)
      val packedDir = Def.declareOutputDirectory(vfDir)
      s.log.debug(s"wrote $vfDir")
      (analysisResult.hasModified(), vfDir: VirtualFileRef, packedDir: HashedVirtualFileRef)
    }
    .tag(Tags.Compile, Tags.CPU)

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

  private def compileIncrementalTaskImpl(
      task: BspCompileTask,
      s: TaskStreams,
      ci: Inputs,
      promise: PromiseWrap[Boolean]
  ): CompileResult = {
    lazy val x = s.text(ExportStream)
    def onArgs(cs: Compilers) =
      cs.withScalac(
        cs.scalac match
          case ac: AnalyzingCompiler => ac.onArgs(exported(x, "scalac"))
          case x                     => x
      )
    def onProgress(s: Setup) =
      val cp = new BspCompileProgress(task, s.progress.asScala)
      s.withProgress(cp)
    val compilers: Compilers = ci.compilers
    val setup: Setup = ci.setup
    val i = ci.withCompilers(onArgs(compilers)).withSetup(onProgress(setup))
    try incCompiler.compile(i, s.log)
    catch
      case e: Throwable =>
        if !promise.isCompleted then
          promise.failure(e)
          ConcurrentRestrictions.cancelAllSentinels()
        throw e
    finally x.close() // workaround for #937
  }

  def compileIncSetupTask = Def.task {
    val cp = dependencyPicklePath.value
    val converter = fileConverter.value
    val cachedAnalysisMap: Map[VirtualFile, CompileAnalysis] = (
      for
        attributed <- cp
        analysis <- BuildDef.extractAnalysis(attributed.metadata, converter)
      yield (converter.toVirtualFile(attributed.data), analysis)
    ).toMap
    val cachedPerEntryDefinesClassLookup: VirtualFile => DefinesClass =
      Keys.classpathEntryDefinesClassVF.value
    val lookup = new PerClasspathEntryLookup:
      override def analysis(classpathEntry: VirtualFile): Optional[CompileAnalysis] =
        cachedAnalysisMap.get(classpathEntry).toOptional
      override def definesClass(classpathEntry: VirtualFile): DefinesClass =
        cachedPerEntryDefinesClassLookup(classpathEntry)
    val extra = extraIncOptions.value.map(t2)
    val store = analysisStore(earlyCompileAnalysisFile)
    val eaOpt = if exportPipelining.value then Some(store) else None
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

  def compileInputsSettings: Seq[Setting[?]] =
    compileInputsSettings(dependencyPicklePath)
  def compileInputsSettings(classpathTask: TaskKey[Classpath]): Seq[Setting[?]] = {
    Seq(
      compileOptions := {
        val c = fileConverter.value
        val cp0 = classpathTask.value
        val cp1 = backendOutput.value +: data(cp0)
        val cp = cp1.map(c.toPath).map(c.toVirtualFile)
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
      },
      // todo: Zinc's hashing should automatically handle directories
      compileInputs2 := {
        val cp0 = classpathTask.value
        val inputs = compileInputs.value
        val c = fileConverter.value
        CompileInputs2(
          data(cp0).toVector,
          inputs.options.sources.toVector,
          scalacOptions.value.toVector,
          javacOptions.value.toVector,
          c.toVirtualFile(inputs.options.classesDirectory),
          c.toVirtualFile(inputs.setup.cacheFile.toPath)
        )
      },
      bspCompileTask :=
        BspCompileTask.start(
          bspTargetIdentifier.value,
          thisProjectRef.value,
          configuration.value,
          compileInputs.value
        )
    )
  }

  private[sbt] def foldMappers(
      mappers: Seq[Position => Option[Position]],
      reportAbsolutePath: Boolean,
      fc: FileConverter
  ) = {
    def withAbsoluteSource(p: Position): Position =
      if (reportAbsolutePath) toAbsoluteSource(fc)(p) else p

    mappers.foldRight({ (p: Position) =>
      withAbsoluteSource(p) // Fallback if sourcePositionMappers is empty
    }) { (mapper, previousPosition) =>
      { (p: Position) =>
        // To each mapper we pass the position with the absolute source (only if reportAbsolutePath = true of course)
        mapper(withAbsoluteSource(p)).getOrElse(previousPosition(p))
      }
    }
  }

  private[sbt] def none[A]: Option[A] = (None: Option[A])
  private[sbt] def jnone[A]: Optional[A] = none[A].toOptional
  def compileAnalysisSettings: Seq[Setting[?]] = Seq(
    previousCompile := {
      val setup = compileIncSetup.value
      val store = analysisStore(compileAnalysisFile)
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

  private inline def analysisStore(inline analysisFile: TaskKey[File]): AnalysisStore =
    MixedAnalyzingCompiler.staticCachedStore(
      analysisFile = analysisFile.value.toPath,
      useTextAnalysis = false,
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
    partialVersion(sbtV) match
      case Some((0, _)) | Some((1, _)) =>
        m.extra(
          PomExtraDependencyAttributes.SbtVersionKey -> sbtV,
          PomExtraDependencyAttributes.ScalaVersionKey -> scalaV
        ).withCrossVersion(Disabled())
      case Some(_) =>
        // this produces a normal suffix like _sjs1_2.13
        val prefix = s"sbt${binarySbtVersion(sbtV)}_"
        m.cross(CrossVersion.binaryWith(prefix, ""))
      case None => sys.error(s"unknown sbt version $sbtV")

  def discoverSbtPluginNames: Initialize[Task[PluginDiscovery.DiscoveredNames]] =
    (Def.task { sbtPlugin.value }).flatMapTask { case p =>
      if p then Def.task(PluginDiscovery.discoverSourceAll(compile.value))
      else Def.task(PluginDiscovery.emptyDiscoveredNames)
    }

  def copyResourcesTask =
    Def.task {
      val t = classDirectory.value
      val dirs = resourceDirectories.value.toSet
      val s = streams.value
      val syncDir = target.value / (prefix(configuration.value.name) + "sync")
      val factory = CacheStoreFactory(syncDir)
      val cacheStore = factory.make("copy-resource")
      val converter = fileConverter.value
      val flt: File => Option[File] = flat(t)
      val transform: File => Option[File] =
        (f: File) => rebase(resourceDirectories.value.sorted, t)(f).orElse(flt(f))
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
      Space ~> token(NotSpace.examples(mainClasses.toSet)) ~ spaceDelimited("<arg>")
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
    val base = token(Space) ~> token(and(NotSpace, not("--", "Unexpected: ---")).examples(exs))
    val recurse = base flatMap { ex =>
      val (matching, notMatching) = exs.partition(GlobFilter(ex).accept)
      distinctParser(notMatching, raw) map { result =>
        if (raw) ex +: result else matching.toSeq ++ result
      }
    }
    recurse ?? Nil
  }

  val CompletionsID = "completions"

  def noAggregation: Seq[Scoped] =
    Seq(run, runMain, bgRun, bgRunMain, console, consoleQuick, consoleProject)
  lazy val disableAggregation =
    Defaults.globalDefaults(noAggregation.map(k => (k / aggregate) :== false))

  // 1. runnerSettings is added unscoped via JvmPlugin.
  // 2. In addition it's added scoped to run task.
  lazy val runnerSettings: Seq[Setting[?]] = Seq(runnerTask, forkOptions := forkOptionsTask.value)
  private lazy val newRunnerSettings: Seq[Setting[?]] =
    Seq(runner := ClassLoaders.runner.value, forkOptions := forkOptionsTask.value)

  lazy val baseTasks: Seq[Setting[?]] = projectTasks ++ packageBase

  lazy val configSettings: Seq[Setting[?]] =
    Classpaths.configSettings ++ configTasks ++ configPaths ++ packageConfig ++
      Classpaths.compilerPluginConfig ++ deprecationSettings ++
      BuildServerProtocol.configSettings

  lazy val compileSettings: Seq[Setting[?]] =
    configSettings ++ (mainBgRunMainTask +: mainBgRunTask) ++ Classpaths.addUnmanagedLibrary

  lazy val testSettings: Seq[Setting[?]] = configSettings ++ testTasks

  @nowarn
  @deprecated(
    "Create a separate subproject instead of using IntegrationTest and in addition avoid using itSettings",
    "1.9.0"
  )
  lazy val itSettings: Seq[Setting[?]] = inConfig(IntegrationTest)(testSettings)
  lazy val defaultConfigs: Seq[Setting[?]] = inConfig(Compile)(compileSettings) ++
    inConfig(Test)(testSettings) ++
    inConfig(Runtime)(Classpaths.configSettings)

  // These are project level settings that MUST be on every project.
  lazy val coreDefaultSettings: Seq[Setting[?]] =
    projectCore ++ disableAggregation ++ Seq(
      // Missing but core settings
      baseDirectory := thisProject.value.base,
      target := baseDirectory.value / "target",
      bgHashClasspath := !turbo.value,
      classLoaderLayeringStrategy := {
        if (turbo.value) ClassLoaderLayeringStrategy.AllLibraryJars
        else ClassLoaderLayeringStrategy.ScalaLibrary
      },
      publishLocal / skip := (publish / skip).value,
      publishM2 / skip := (publish / skip).value
    )
  // build.sbt is treated a Scala source of metabuild, so to enable deprecation flag on build.sbt we set the option here.
  lazy val deprecationSettings: Seq[Setting[?]] =
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

  lazy val dependencyResolutionTask: Def.Initialize[Task[DependencyResolution]] =
    Def.task {
      CoursierDependencyResolution(csrConfiguration.value)
    }

  def templateRunLocalInputTask(
      runLocal: (Seq[String], Logger) => Unit
  ): Initialize[InputTask[Unit]] =
    Def.inputTask {
      import Def._
      val s = streams.value
      val args = spaceDelimited().parsed
      runLocal(args, s.log)
    }

  def runLocalTemplate(arguments: Seq[String], log: Logger): Unit =
    TemplateCommandUtil.defaultRunLocalTemplate(arguments.toList, log)
}

object Classpaths {
  import Defaults._
  import Keys._

  def analyzed[A](data: A, analysisFile: VirtualFile): Attributed[A] =
    Attributed.blank(data).put(Keys.analysis, analysisFile.id)

  def concatDistinct[A](
      a: Taskable[Seq[A]],
      b: Taskable[Seq[A]]
  ): Initialize[Task[Seq[A]]] =
    Def.task((a.toTask.value ++ b.toTask.value).distinct)

  def concat[A](a: Taskable[Seq[A]], b: Taskable[Seq[A]]): Initialize[Task[Seq[A]]] =
    Def.task(a.toTask.value ++ b.toTask.value)

  def concatSettings[T](a: Initialize[Seq[T]], b: Initialize[Seq[T]]): Initialize[Seq[T]] =
    Def.setting { a.value ++ b.value }

  def concatDistinct[A]( // forward to widened variant
      a: ScopedTaskable[Seq[A]],
      b: ScopedTaskable[Seq[A]]
  ): Initialize[Task[Seq[A]]] = concatDistinct(a: Taskable[Seq[A]], b)

  def concat[A](
      a: ScopedTaskable[Seq[A]],
      b: ScopedTaskable[Seq[A]]
  ): Initialize[Task[Seq[A]]] =
    concat(a: Taskable[Seq[A]], b) // forward to widened variant

  def concatSettings[T](a: SettingKey[Seq[T]], b: SettingKey[Seq[T]]): Initialize[Seq[T]] =
    concatSettings(a: Initialize[Seq[T]], b) // forward to widened variant

  // Included as part of JvmPlugin#projectSettings.
  lazy val configSettings: Seq[Setting[?]] = classpaths ++ Seq(
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
  private def classpaths: Seq[Setting[?]] =
    Seq(
      externalDependencyClasspath := concat(unmanagedClasspath, managedClasspath).value,
      dependencyClasspath := concat(internalDependencyClasspath, externalDependencyClasspath).value,
      fullClasspath := concatDistinct(exportedProducts, dependencyClasspath).value,
      internalDependencyClasspath := ClasspathImpl.internalDependencyClasspathTask.value,
      unmanagedClasspath := ClasspathImpl.unmanagedDependenciesTask.value,
      managedClasspath := {
        val converter = fileConverter.value
        val isMeta = isMetaBuild.value
        val force = reresolveSbtArtifacts.value
        val app = appConfiguration.value
        def isJansiOrJLine(f: File) = f.getName.contains("jline") || f.getName.contains("jansi")
        val scalaInstanceJars = app.provider.scalaProvider.jars.filterNot(isJansiOrJLine)
        val sbtCp = (scalaInstanceJars ++ app.provider.mainClasspath)
          .map(_.toPath)
          .map(p => converter.toVirtualFile(p): HashedVirtualFileRef)
          .map(Attributed.blank)
        val mjars = managedJars(
          classpathConfiguration.value,
          classpathTypes.value,
          update.value,
          converter,
        )
        if isMeta && !force then (mjars ++ sbtCp).distinct
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
      dependencyClasspathAsJars := concat(
        internalDependencyAsJars,
        externalDependencyClasspath
      ).value,
      fullClasspathAsJars := concatDistinct(exportedProductJars, dependencyClasspathAsJars).value,
      unmanagedJars := findUnmanagedJars(
        configuration.value,
        unmanagedBase.value,
        (unmanagedJars / includeFilter).value,
        (unmanagedJars / excludeFilter).value,
        fileConverter.value,
      )
    ).map(exportVirtualClasspath) ++ Seq(
      externalDependencyClasspath / outputFileStamps := {
        val stamper = timeWrappedStamper.value
        val converter = fileConverter.value
        externalDependencyClasspath.value.flatMap: vf =>
          val p = converter.toPath(vf.data)
          FileStamp(stamper.library(vf.data)).map(p -> _)
      },
      dependencyClasspathFiles := {
        val converter = fileConverter.value
        data(dependencyClasspath.value).map(converter.toPath)
      },
      dependencyClasspathFiles / outputFileStamps := {
        val stamper = timeWrappedStamper.value
        val converter = fileConverter.value
        dependencyClasspathFiles.value.flatMap: p =>
          val vf = converter.toVirtualFile(p)
          FileStamp(stamper.library(vf)).map(p -> _)
      },
      // Note: invoking this task from shell would block indefinately because it will
      // wait for the upstream compilation to start.
      dependencyPicklePath := {
        // This is a conditional task. Do not refactor.
        if (incOptions.value.pipelining) {
          concat(
            internalDependencyPicklePath,
            externalDependencyClasspath,
          ).value
        } else {
          dependencyClasspath.value
        }
      },
      internalDependencyPicklePath := ClasspathImpl.internalDependencyPicklePathTask.value,
      exportedPickles := ClasspathImpl.exportedPicklesTask.value,
    )
  private def exportVirtualClasspath(
      s: Setting[Task[Classpath]]
  ): Setting[Task[Classpath]] =
    s.mapInitialize(init => Def.task { exportVirtualClasspath(streams.value, init.value) })
  private def exportClasspath(
      s: Setting[Task[Seq[Attributed[File]]]]
  ): Setting[Task[Seq[Attributed[File]]]] =
    s.mapInitialize(init => Def.task { exportClasspath(streams.value, init.value) })
  private def exportVirtualClasspath(s: TaskStreams, cp: Classpath): Classpath =
    val w = s.text(ExportStream)
    try w.println(data(cp).toString)
    finally w.close() // workaround for #937
    cp
  private def exportClasspath(
      s: TaskStreams,
      cp: Seq[Attributed[File]]
  ): Seq[Attributed[File]] =
    val w = s.text(ExportStream)
    try w.println(Path.makeString(data(cp)))
    finally w.close() // workaround for #937
    cp

  def defaultPackageKeys = Seq(packageBin, packageSrc, packageDoc)
  lazy val defaultPackages: Seq[TaskKey[HashedVirtualFileRef]] =
    for
      task <- defaultPackageKeys
      conf <- Seq(Compile, Test)
    yield (conf / task)
  lazy val defaultArtifactTasks: Seq[TaskKey[HashedVirtualFileRef]] = makePom +: defaultPackages

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

  def packaged(
      pkgTasks: Seq[TaskKey[HashedVirtualFileRef]]
  ): Initialize[Task[Map[Artifact, HashedVirtualFileRef]]] =
    enabledOnly(packagedArtifact.toSettingKey, pkgTasks).apply(_.join.map(_.toMap))

  def artifactDefs(pkgTasks: Seq[TaskKey[HashedVirtualFileRef]]): Initialize[Seq[Artifact]] =
    enabledOnly(artifact, pkgTasks)

  def enabledOnly[T](
      key: SettingKey[T],
      pkgTasks: Seq[TaskKey[HashedVirtualFileRef]]
  ): Initialize[Seq[T]] =
    (forallIn(key, pkgTasks).zipWith(forallIn(publishArtifact, pkgTasks)))(_ zip _ collect {
      case (a, true) => a
    })

  def forallIn[T](
      key: Scoped.ScopingSetting[SettingKey[T]], // should be just SettingKey[T] (mea culpa)
      pkgTasks: Seq[TaskKey[?]],
  ): Initialize[Seq[T]] =
    pkgTasks.map(pkg => (pkg.scope / pkg / key)).join

  private def publishGlobalDefaults =
    Defaults.globalDefaults(
      Seq(
        publishMavenStyle :== true,
        sbtPluginPublishLegacyMavenStyle :== false,
        publishArtifact :== true,
        (Test / publishArtifact) :== false
      )
    )

  private lazy val publishSbtPluginMavenStyle = Def.task(sbtPlugin.value && publishMavenStyle.value)
  private lazy val packagedDefaultArtifacts = packaged(defaultArtifactTasks)
  private lazy val sbt2Plus: Def.Initialize[Boolean] = Def.setting {
    val sbtV = (pluginCrossBuild / sbtBinaryVersion).value
    sbtV != "1.0" && !sbtV.startsWith("0.")
  }
  val jvmPublishSettings: Seq[Setting[?]] = Seq(
    artifacts := artifactDefs(defaultArtifactTasks).value,
    packagedArtifacts := Def
      .ifS(publishSbtPluginMavenStyle)(mavenArtifactsOfSbtPlugin)(packagedDefaultArtifacts)
      .value,
    // publishLocal needs legacy artifacts (see https://github.com/sbt/sbt/issues/7285)
    publishLocal / packagedArtifacts ++= {
      if (sbtPlugin.value && !sbtPluginPublishLegacyMavenStyle.value) {
        packagedDefaultArtifacts.value
      } else Map.empty[Artifact, HashedVirtualFileRef]
    }
  ) ++ RemoteCache.projectSettings

  /**
   * Produces the Maven-compatible artifacts of an sbt plugin.
   * It adds the sbt-cross version suffix into the artifact names, and it generates a
   * valid POM file, that is a POM file that Maven can resolve.
   */
  private def mavenArtifactsOfSbtPlugin: Def.Initialize[Task[Map[Artifact, HashedVirtualFileRef]]] =
    Def.task {
      // This is a conditional task. The top-level must be an if expression.
      if (sbt2Plus.value) {
        // Both POMs and JARs are Maven-compatible in sbt 2.x, so ignore the workarounds
        packagedDefaultArtifacts.value
      } else {
        val sbtV = (pluginCrossBuild / sbtBinaryVersion).value
        val scalaV = scalaBinaryVersion.value
        val crossVersion = (name: String) => name + s"_${scalaV}_$sbtV"
        val legacyPomArtifact = (makePom / artifact).value
        val converter = fileConverter.value
        def addSuffix(a: Artifact): Artifact = a.withName(crossVersion(a.name))
        Map(addSuffix(legacyPomArtifact) -> makeMavenPomOfSbtPlugin(converter, crossVersion)) ++
          pomConsistentArtifactsForLegacySbt(converter, crossVersion) ++
          legacyPackagedArtifacts.value
      }
    }

  private def legacyPackagedArtifacts: Def.Initialize[Task[Map[Artifact, HashedVirtualFileRef]]] =
    Def.task {
      // This is a conditional task. The top-level must be an if expression.
      if (sbtPluginPublishLegacyMavenStyle.value) packagedDefaultArtifacts.value
      else Map.empty[Artifact, HashedVirtualFileRef]
    }

  private inline def pomConsistentArtifactsForLegacySbt(
      converter: FileConverter,
      crossVersion: String => String
  ): Map[Artifact, HashedVirtualFileRef] =
    val legacyPackages = packaged(defaultPackages).value
    def copyArtifact(
        artifact: Artifact,
        fileRef: HashedVirtualFileRef
    ): (Artifact, HashedVirtualFileRef) = {
      val nameWithSuffix = crossVersion(artifact.name)
      val file = converter.toPath(fileRef).toFile
      val targetFile =
        new File(file.getParentFile, file.name.replace(artifact.name, nameWithSuffix))
      IO.copyFile(file, targetFile)
      artifact.withName(nameWithSuffix) -> converter.toVirtualFile(targetFile.toPath)
    }
    legacyPackages.map { case (artifact, file) =>
      copyArtifact(artifact, file);
    }

  /**
   * Generates a POM file that Maven can resolve.
   * It appends the sbt cross version into all artifactIds of sbt plugins
   * (the main one and the dependencies).
   */
  private inline def makeMavenPomOfSbtPlugin(
      converter: FileConverter,
      crossVersion: String => String
  ): HashedVirtualFileRef =
    val config = makePomConfiguration.value
    val nameWithCross = crossVersion(artifact.value.name)
    val version = Keys.version.value
    val pomFile = config.file.get.getParentFile / s"$nameWithCross-$version.pom"
    val publisher = Keys.publisher.value
    val ivySbt = Keys.ivySbt.value
    val module = new ivySbt.Module(moduleSettings.value, appendSbtCrossVersion = true)
    publisher.makePomFile(module, config.withFile(pomFile), streams.value.log)
    converter.toVirtualFile(pomFile.toPath)

  def ivyPublishSettings: Seq[Setting[?]] = publishGlobalDefaults ++ Seq(
    artifacts :== Nil,
    packagedArtifacts :== Map.empty,
    makePom := {
      val converter = fileConverter.value
      val config = makePomConfiguration.value
      val publisher = Keys.publisher.value
      publisher.makePomFile(ivyModule.value, config, streams.value.log)
      converter.toVirtualFile(config.file.get.toPath())
    },
    (makePom / packagedArtifact) := ((makePom / artifact).value -> makePom.value),
    deliver := deliverTask(makeIvyXmlConfiguration).value,
    deliverLocal := deliverTask(makeIvyXmlLocalConfiguration).value,
    makeIvyXml := deliverTask(makeIvyXmlConfiguration).value,
    publish := publishOrSkip(publishConfiguration, publish / skip).value,
    publishLocal := publishOrSkip(publishLocalConfiguration, publishLocal / skip).value,
    publishM2 := publishOrSkip(publishM2Configuration, publishM2 / skip).value
  )

  def baseGlobalDefaults =
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
        ivyLoggingLevel := ( // This will suppress "Resolving..." logs on Jenkins and Travis.
          if (insideCI.value)
            UpdateLogging.Quiet
          else UpdateLogging.Default
        ),
        ivyXML :== NodeSeq.Empty,
        ivyValidate :== false,
        moduleConfigurations :== Nil,
        publishTo :== None,
        resolvers :== Vector.empty,
        includePluginResolvers :== false,
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
          val base = ModuleID(id.groupID, id.name, sbtVersion.value)
            .withCrossVersion(cross)
            .platform(Platform.jvm)
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

  def ivyBaseSettings: Seq[Setting[?]] = baseGlobalDefaults ++ sbtClassifiersTasks ++ Seq(
    conflictWarning := conflictWarning.value.copy(label = Reference.display(thisProjectRef.value)),
    unmanagedBase := baseDirectory.value / "lib",
    normalizedName := Project.normalizeModuleID(name.value),
    isSnapshot := (isSnapshot or version(_.endsWith("-SNAPSHOT"))).value,
    description := (description or name).value,
    organization := (organization or normalizedName).value,
    organizationName := (organizationName or organization).value,
    organizationHomepage := (organizationHomepage or homepage).value,
    projectInfo := ModuleInfo(
      name.value,
      description.value,
      homepage.value.map(_.toURI),
      startYear.value,
      licenses.value.map((name, url) => (name, url.toURI)).toVector,
      organizationName.value,
      organizationHomepage.value.map(_.toURI),
      scmInfo.value,
      developers.value.toVector
    ),
    overrideBuildResolvers := appConfiguration(isOverrideRepositories).value,
    externalResolvers := ((
      externalResolvers.?.value,
      resolvers.value,
      appResolvers.value,
    ) match {
      case (Some(delegated), Seq(), _) => delegated
      case (_, rs, Some(ars))          => ars ++ rs
      case (_, rs, _) =>
        Resolver.combineDefaultResolvers(rs.toVector, jcenter = false, mavenCentral = true)
    }),
    appResolvers := {
      val ac = appConfiguration.value
      appRepositories(ac) map { ars =>
        val useMavenCentral = ars contains Resolver.DefaultMavenRepository
        Resolver.reorganizeAppResolvers(ars, jcenter = false, useMavenCentral)
      }
    },
    bootResolvers := {
      appConfiguration.map(bootRepositories).value
    },
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
    outputPath := {
      val p = platform.value
      val m = moduleName.value
      val sv = scalaVersion.value
      s"$p/scala-$sv/$m"
    },
    ivyPaths := IvyPaths(
      baseDirectory.value.toString,
      bootIvyHome(appConfiguration.value).map(_.toString)
    ),
    csrCacheDirectory := {
      val old = csrCacheDirectory.value
      val ac = appConfiguration.value
      val ip = ivyPaths.value
      // if ivyPaths is customized, create coursier-cache directory in it
      val defaultIvyCache = bootIvyHome(ac).map(_.toString)
      if (old != LMCoursier.defaultCacheLocation) old
      else if (ip.ivyHome == defaultIvyCache) old
      else
        ip.ivyHome match {
          case Some(home) => new File(home) / "coursier-cache"
          case _          => old
        }
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
            .withPlatform(platform.?.value)
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

      confs ++ extraSources.toSeq ++ extraDocs.toSeq
    },
    moduleSettings := moduleSettings0.value,
    makePomConfiguration := {
      val converter = fileConverter.value
      val out = converter.toPath((makePom / artifactPath).value)
      MakePomConfiguration()
        .withFile(out.toFile())
        .withModuleInfo(projectInfo.value)
        .withExtra(pomExtra.value)
        .withProcess(pomPostProcess.value)
        .withFilterRepositories(pomIncludeRepository.value)
        .withAllRepositories(pomAllRepositories.value)
        .withConfigurations(Configurations.defaultMavenConfigurations)
    },
    makeIvyXmlConfiguration := {
      makeIvyXmlConfig(
        publishMavenStyle.value,
        sbt.Classpaths.deliverPattern(target.value),
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
      val converter = fileConverter.value
      val artifacts = (publish / packagedArtifacts).value.toVector.map { (a, vf) =>
        a -> converter.toPath(vf).toFile
      }
      publishConfig(
        publishMavenStyle.value,
        deliverPattern(target.value),
        if (isSnapshot.value) "integration" else "release",
        ivyConfigurations.value.map(c => ConfigRef(c.name)).toVector,
        artifacts,
        (publish / checksums).value.toVector,
        getPublishTo(publishTo.value).name,
        ivyLoggingLevel.value,
        isSnapshot.value
      )
    },
    makeIvyXmlLocalConfiguration := {
      makeIvyXmlConfig(
        false, // publishMavenStyle.value,
        sbt.Classpaths.deliverPattern(target.value),
        if (isSnapshot.value) "integration" else "release",
        ivyConfigurations.value.map(c => ConfigRef(c.name)).toVector,
        (publish / checksums).value.toVector,
        ivyLoggingLevel.value,
        isSnapshot.value,
        optResolverName = Some("local")
      )
    },
    publishLocalConfiguration := {
      val converter = fileConverter.value
      val artifacts = (publishLocal / packagedArtifacts).value.toVector.map { (a, vf) =>
        a -> converter.toPath(vf).toFile
      }
      publishConfig(
        false, // publishMavenStyle.value,
        deliverPattern(target.value),
        if (isSnapshot.value) "integration" else "release",
        ivyConfigurations.value.map(c => ConfigRef(c.name)).toVector,
        artifacts,
        (publishLocal / checksums).value.toVector,
        logging = ivyLoggingLevel.value,
        overwrite = isSnapshot.value
      )
    },
    publishM2Configuration := {
      val converter = fileConverter.value
      val artifacts = (publishM2 / packagedArtifacts).value.toVector.map { (a, vf) =>
        a -> converter.toPath(vf).toFile
      }
      publishConfig(
        true,
        deliverPattern(target.value),
        if (isSnapshot.value) "integration" else "release",
        ivyConfigurations.value.map(c => ConfigRef(c.name)).toVector,
        artifacts,
        checksums = (publishM2 / checksums).value.toVector,
        resolverName = Resolver.publishMavenLocal.name,
        logging = ivyLoggingLevel.value,
        overwrite = isSnapshot.value
      )
    },
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
    updateFull := updateTask.value,
    update := updateWithoutDetails("update").value,
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
      val report = updateTask.value
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
        TaskZero / updateClassifiers := LibraryManagement.updateClassifiersTask.value,
      )
    ) ++ Seq(
      csrProject := CoursierInputsTasks.coursierProjectTask.value,
      csrConfiguration := LMCoursier.coursierConfigurationTask.value,
      csrResolvers := CoursierRepositoriesTasks.coursierResolversTask(fullResolvers).value,
      csrRecursiveResolvers := CoursierRepositoriesTasks.coursierRecursiveResolversTask.value,
      csrSbtResolvers := CoursierRepositoriesTasks.coursierSbtResolversTask.value,
      csrInterProjectDependencies := CoursierInputsTasks.coursierInterProjectDependenciesTask.value,
      csrExtraProjects := CoursierInputsTasks.coursierExtraProjectsTask.value,
      csrFallbackDependencies := CoursierInputsTasks.coursierFallbackDependenciesTask.value,
    ) ++
    IvyXml.generateIvyXmlSettings() ++
    LMCoursier.publicationsSetting(Seq(Compile, Test).map(c => c -> CConfiguration(c.name)))

  def jvmBaseSettings: Seq[Setting[?]] = Seq(
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
        if scalaHome.value.isDefined || scalaModuleInfo.value.isEmpty || !managedScalaInstance.value
        then Nil
        else
          ScalaArtifacts.toolDependencies(sbtOrg, version) ++
            ScalaArtifacts.docToolDependencies(sbtOrg, version)
      allToolDeps.map(_.platform(Platform.jvm)) ++ pluginAdjust
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
      .cross((projectID / crossVersion).value)
      .artifacts(artifacts.value*)
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
    val p3 = releaseNotesURL.value match {
      case Some(u) =>
        p2.extra(SbtPomExtraProperties.POM_RELEASE_NOTES_KEY -> u.toExternalForm)
      case _ => p2
    }
    p3
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
  private[sbt] lazy val ivySbt0: Initialize[Task[IvySbt]] =
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

  private def sbtClassifiersGlobalDefaults =
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
            resolutionCacheDir = Some(target.value / "resolution-cache"),
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
          (TaskZero / updateSbtClassifiers) := (Def
            .task {
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
            }
            .tag(Tags.Update, Tags.Network))
            .value
        )
      ) ++
      inTask(scalaCompilerBridgeScope)(
        Seq(
          dependencyResolution := dependencyResolutionTask.value,
          csrConfiguration := LMCoursier.scalaCompilerBridgeConfigurationTask.value,
          csrResolvers :=
            CoursierRepositoriesTasks.coursierResolversTask(scalaCompilerBridgeResolvers).value,
          externalResolvers := scalaCompilerBridgeResolvers.value,
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

  def classifiersModuleTask: Initialize[Task[GetClassifiersModule]] =
    Def.task {
      val classifiers = transitiveClassifiers.value
      val ref = thisProjectRef.value
      val unit = loadedBuild.value.units(ref.build).unit
      val converter = unit.converter
      val pluginClasspath = unit.plugins.fullClasspath.toVector
      val pluginJars = pluginClasspath.filter: x =>
        !Files.isDirectory(converter.toPath(x.data))
      // exclude directories: an approximation to whether they've been published
      val pluginIDs: Vector[ModuleID] = pluginJars.flatMap(_.get(moduleIDStr).map: str =>
        moduleIdJsonKeyFormat.read(str))
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
      deliverKey: TaskKey[?],
  ): Initialize[Task[Unit]] =
    publishTask(config)

  private def logSkipPublish(log: Logger, ref: ProjectRef): Unit =
    log.debug(s"Skipping publish* for ${ref.project}")

  @deprecated("use publishOrSkip instead", "1.9.1")
  def publishTask(config: TaskKey[PublishConfiguration]): Initialize[Task[Unit]] = {
    val skipKey =
      if (config.key == publishLocalConfiguration.key) publishLocal / skip
      else publish / skip
    publishOrSkip(config, skipKey)
  }

  def publishOrSkip(
      config: TaskKey[PublishConfiguration],
      skip: TaskKey[Boolean]
  ): Initialize[Task[Unit]] =
    Def
      .taskIf {
        if (skip.value) {
          val log = streams.value.log
          val ref = thisProjectRef.value
          logSkipPublish(log, ref)
        } else {
          val conf = config.value
          val log = streams.value.log
          val module = ivyModule.value
          val publisherInterface = publisher.value
          publisherInterface.publish(module, conf, log)
        }
      }
      .tag(Tags.Publish, Tags.Network)

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

  lazy val updateTask: Initialize[Task[UpdateReport]] =
    updateTask0("updateFull", true, true).tag(Tags.Update, Tags.Network)
  def updateWithoutDetails(label: String): Initialize[Task[UpdateReport]] =
    updateTask0(label, false, false).tag(Tags.Update, Tags.Network)

  /**
   * cacheLabel - label to identify an update cache
   * includeCallers - include the caller information
   * includeDetails - include module reports for the evicted modules
   */
  private def updateTask0(
      cacheLabel: String,
      includeCallers: Boolean,
      includeDetails: Boolean
  ): Initialize[Task[UpdateReport]] =
    TupleWrap[
      (
          DependencyResolution,
          TaskStreams,
          UpdateConfiguration,
          Option[Level.Value],
          String,
          State,
          String,
          xsbti.AppConfiguration,
          Option[ScalaInstance],
          File,
          File,
          Seq[ScopedKey[?]],
          ScopedKey[?],
          Option[FiniteDuration],
          Boolean,
          ProjectRef,
          IvySbt#Module,
          String,
          Boolean,
          Seq[UpdateReport],
          UnresolvedWarningConfiguration,
          Level.Value,
          Seq[ModuleID],
          Level.Value,
          String,
          String,
          Boolean,
          CompatibilityWarningOptions,
      )
    ](
      dependencyResolution,
      streams,
      updateConfiguration.toTaskable,
      (update / logLevel).?.toTaskable,
      updateCacheName.toTaskable,
      state,
      scalaVersion.toTaskable,
      appConfiguration.toTaskable,
      Defaults.unmanagedScalaInstanceOnly.toTaskable,
      dependencyCacheDirectory.toTaskable,
      target.toTaskable,
      executionRoots.toTaskable,
      resolvedScoped.toTaskable,
      forceUpdatePeriod.toTaskable,
      sbtPlugin.toTaskable,
      thisProjectRef.toTaskable,
      ivyModule.toTaskable,
      scalaOrganization.toTaskable,
      (update / skip).toTaskable,
      transitiveUpdate.toTaskable,
      (update / unresolvedWarningConfiguration).toTaskable,
      evictionErrorLevel.toTaskable,
      libraryDependencySchemes.toTaskable,
      assumedEvictionErrorLevel.toTaskable,
      assumedVersionScheme.toTaskable,
      assumedVersionSchemeJava.toTaskable,
      publishMavenStyle.toTaskable,
      compatibilityWarningOptions.toTaskable,
    ).mapN {
      case (
            lm,
            s,
            conf,
            maybeUpdateLevel,
            ucn,
            state0,
            sv,
            ac,
            usiOnly,
            dcd,
            ct,
            er,
            rs,
            fup,
            isPlugin,
            thisRef,
            im,
            so,
            sk,
            tu,
            uwConfig,
            eel,
            lds,
            aeel,
            avs,
            avsj,
            mavenStyle,
            cwo,
          ) =>
        val cacheDirectory = ct / cacheLabel / ucn
        val cacheStoreFactory: CacheStoreFactory = {
          val factory =
            state0.get(Keys.cacheStoreFactoryFactory).getOrElse(InMemoryCacheStore.factory(0))
          factory(cacheDirectory.toPath)
        }

        val isRoot = er.contains(rs)
        val shouldForce = isRoot || {
          fup match
            case None => false
            case Some(period) =>
              val fullUpdateOutput = cacheDirectory / "output"
              val now = System.currentTimeMillis
              val diff = now - IO.getModifiedTimeOrZero(fullUpdateOutput)
              val elapsedDuration = new FiniteDuration(diff, TimeUnit.MILLISECONDS)
              fullUpdateOutput.exists() && elapsedDuration > period
        }

        val providedScalaJars: String => Seq[File] = {
          val scalaProvider = ac.provider.scalaProvider
          usiOnly match
            case Some(instance) =>
              unmanagedJarsTask(sv, instance.version, instance.allJars)
            case None =>
              (subVersion: String) =>
                if (scalaProvider.version == subVersion) scalaProvider.jars else Nil
        }
        val updateConf = {
          // Log captures log messages at all levels, except ivy logs.
          // Use full level when debug is enabled so that ivy logs are shown.
          import UpdateLogging.{ Default, DownloadOnly, Full }
          val conf1 = maybeUpdateLevel.orElse(state0.get(logLevel.key)) match {
            case Some(Level.Debug) if conf.logging == Default => conf.withLogging(logging = Full)
            case Some(_) if conf.logging == Default => conf.withLogging(logging = DownloadOnly)
            case _                                  => conf
          }

          // logical clock is folded into UpdateConfiguration
          conf1
            .withLogicalClock(LogicalClock(state0.hashCode))
            .withMetadataDirectory(dcd)
        }

        val extracted = Project.extract(state0)
        val label =
          if (isPlugin) Reference.display(thisRef)
          else Def.displayRelativeReference(extracted.currentRef, thisRef)

        LibraryManagement.cachedUpdate(
          // LM API
          lm = lm,
          // Ivy-free ModuleDescriptor
          module = im,
          cacheStoreFactory = cacheStoreFactory,
          label = label,
          updateConf,
          substituteScalaFiles(so, _)(providedScalaJars),
          skip = sk,
          force = shouldForce,
          depsUpdated = tu.exists(!_.stats.cached),
          uwConfig = uwConfig,
          evictionLevel = eel,
          versionSchemeOverrides = lds,
          assumedEvictionErrorLevel = aeel,
          assumedVersionScheme = avs,
          assumedVersionSchemeJava = avsj,
          mavenStyle = mavenStyle,
          compatWarning = cwo,
          includeCallers = includeCallers,
          includeDetails = includeDetails,
          log = s.log
        )
    }: @nowarn

  private[sbt] def dependencyPositionsTask: Initialize[Task[Map[ModuleID, SourcePosition]]] =
    Def.task {
      val projRef = thisProjectRef.value
      val st = state.value
      val s = streams.value
      val cacheStoreFactory = s.cacheStoreFactory.sub(updateCacheName.value)
      import sbt.librarymanagement.LibraryManagementCodec._
      def modulePositions: Map[ModuleID, SourcePosition] =
        try {
          val extracted = Project.extract(st)
          val sk = (projRef / Zero / Zero / libraryDependencies).scopedKey
          val empty = extracted.structure.data.set(sk, Nil)
          val settings = extracted.structure.settings filter { (s: Setting[?]) =>
            (s.key.key == libraryDependencies.key) &&
            (s.key.scope.project == Select(projRef))
          }
          settings
            .asInstanceOf[Seq[Setting[Seq[ModuleID]]]]
            .flatMap(s => s.init.evaluate(empty).map(_ -> s.pos))
            .toMap
        } catch {
          case NonFatal(_) => Map()
        }

      val outCacheStore = cacheStoreFactory.make("output_dsp")
      val f = Tracked.inputChanged(cacheStoreFactory.make("input_dsp")) {
        (inChanged: Boolean, in: Seq[ModuleID]) =>
          given NoPositionFormat: JsonFormat[NoPosition.type] = asSingleton(NoPosition)
          given LinePositionFormat: IsoLList.Aux[LinePosition, String :*: Int :*: LNil] =
            LList.iso(
              { (l: LinePosition) =>
                ("path", l.path) :*: ("startLine", l.startLine) :*: LNil
              },
              { (in: String :*: Int :*: LNil) =>
                LinePosition(in.head, in.tail.head)
              }
            )
          given LineRangeFormat: IsoLList.Aux[LineRange, Int :*: Int :*: LNil] = LList.iso(
            { (l: LineRange) =>
              ("start", l.start) :*: ("end", l.end) :*: LNil
            },
            { (in: Int :*: Int :*: LNil) =>
              LineRange(in.head, in.tail.head)
            }
          )
          given RangePositionFormat: IsoLList.Aux[RangePosition, String :*: LineRange :*: LNil] =
            LList.iso(
              { (r: RangePosition) =>
                ("path", r.path) :*: ("range", r.range) :*: LNil
              },
              { (in: String :*: LineRange :*: LNil) =>
                RangePosition(in.head, in.tail.head)
              }
            )
          given SourcePositionFormat: JsonFormat[SourcePosition] =
            unionFormat3[SourcePosition, NoPosition.type, LinePosition, RangePosition]

          given midJsonKeyFmt: sjsonnew.JsonKeyFormat[ModuleID] = moduleIdJsonKeyFormat
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

  private[sbt] def isScala213(sv: String) = sv.startsWith("2.13.")

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
    import sbt.TupleSyntax.*
    (buildDependencies.toTaskable, thisProjectRef.toTaskable, settingsData, streams).flatMapN {
      case (bd, thisProj, data, s) =>
        depMap(bd.classpathTransitiveRefs(thisProj), data, s.log)
    }

  private[sbt] def depMap(
      projects: Seq[ProjectRef],
      data: Def.Settings,
      log: Logger
  ): Task[Map[ModuleRevisionId, ModuleDescriptor]] =
    val ivyModules = projects.flatMap { proj =>
      (proj / ivyModule).get(data)
    }.join
    ivyModules.mapN { mod =>
      mod.map { _.dependencyMapping(log) }.toMap
    }

  def projectResolverTask: Initialize[Task[Resolver]] =
    projectDescriptors.map { m =>
      val resolver = new ProjectResolver(ProjectResolver.InterProject, m)
      new RawRepository(resolver, resolver.getName)
    }

  def makeProducts: Initialize[Task[Seq[File]]] = Def.task {
    val c = fileConverter.value
    val resources = copyResources.value.map(_._2).toSet
    val classDir = classDirectory.value
    val vfBackendDir = compileIncremental.value._2
    val backendDir = c.toPath(vfBackendDir)
    // delete outdated files
    Path
      .allSubpaths(classDir)
      .collect { case (f, _) if f.isFile() && !resources.contains(f) => f }
      .foreach(IO.delete)
    IO.copyDirectory(
      source = backendDir.toFile(),
      target = classDir,
    )
    classDir :: Nil
  }

  private[sbt] def makePickleProducts: Initialize[Task[Seq[VirtualFile]]] = Def.task {
    // This is a conditional task.
    if (earlyOutputPing.await.value) {
      // TODO: copyResources.value
      earlyOutput.value :: Nil
    } else {
      val c = fileConverter.value
      products.value map { (x: File) =>
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
  lazy val mkIvyConfiguration: Initialize[Task[InlineIvyConfiguration]] =
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
        .withResolutionCacheDir(target.value / "resolution-cache")
        .withUpdateOptions(updateOptions.value)
        .withLog(s.log)
    }

  def interSort(
      projectRef: ProjectRef,
      conf: Configuration,
      data: Def.Settings,
      deps: BuildDependencies
  ): Seq[(ProjectRef, String)] = ClasspathImpl.interSort(projectRef, conf, data, deps)

  def interSortConfigurations(
      projectRef: ProjectRef,
      conf: Configuration,
      data: Def.Settings,
      deps: BuildDependencies
  ): Seq[(ProjectRef, ConfigRef)] =
    interSort(projectRef, conf, data, deps).map { case (projectRef, configName) =>
      (projectRef, ConfigRef(configName))
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

  def getConfigurations(p: ResolvedReference, data: Def.Settings): Seq[Configuration] =
    ClasspathImpl.getConfigurations(p, data)
  def confOpt(configurations: Seq[Configuration], conf: String): Option[Configuration] =
    ClasspathImpl.confOpt(configurations, conf)

  def unmanagedLibs(dep: ResolvedReference, conf: String, data: Def.Settings): Task[Classpath] =
    ClasspathImpl.unmanagedLibs(dep, conf, data)

  def getClasspath(
      key: TaskKey[Classpath],
      dep: ResolvedReference,
      conf: String,
      data: Def.Settings
  ): Task[Classpath] =
    ClasspathImpl.getClasspath(key, dep, conf, data)

  def defaultConfigurationTask(p: ResolvedReference, data: Def.Settings): Configuration =
    (p / defaultConfiguration).get(data).flatten.getOrElse(Configurations.Default)

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
      modifyForPlugin(plugin, ScalaArtifacts.libraryDependency(org, version))
        .platform(Platform.jvm) :: Nil
    else
      Nil

  def addUnmanagedLibrary: Seq[Setting[?]] =
    Seq((Compile / unmanagedJars) ++= unmanagedScalaLibrary.value)

  def unmanagedScalaLibrary: Initialize[Task[Seq[HashedVirtualFileRef]]] =
    (Def.task { autoScalaLibrary.value && scalaHome.value.isDefined }).flatMapTask { case cond =>
      if cond then
        Def.task {
          val converter = fileConverter.value
          scalaInstance.value.libraryJars.toSeq
            .map(_.toPath)
            .map(converter.toVirtualFile)
        }
      else Def.task { Seq.empty[HashedVirtualFileRef] }
    }

  import DependencyFilter._
  def managedJars(
      config: Configuration,
      jarTypes: Set[String],
      up: UpdateReport,
      converter: FileConverter
  ): Classpath =
    up.filter(configurationFilter(config.name) && artifactFilter(`type` = jarTypes))
      .toSeq
      .map { case (_, module, art, file) =>
        val vf = converter.toVirtualFile(file.toPath())
        Attributed(vf)(
          Map(
            Keys.artifactStr -> RemoteCache.artifactToStr(art),
            Keys.moduleIDStr -> moduleIdJsonKeyFormat.write(module),
            Keys.configurationStr -> config.name,
          )
        )
      }
      .distinct

  def findUnmanagedJars(
      config: Configuration,
      base: File,
      filter: FileFilter,
      excl: FileFilter,
      converter: FileConverter,
  ): Classpath =
    given FileConverter = converter
    (base * (filter -- excl) +++ (base / config.name).descendantsExcept(filter, excl)).classpath

  def autoPlugins(
      report: UpdateReport,
      internalPluginClasspath: Seq[NioPath],
      isDotty: Boolean
  ): Seq[String] =
    import sbt.internal.inc.classpath.ClasspathUtil.compilerPlugins
    val pluginClasspath =
      report
        .matching(configurationFilter(CompilerPlugin.name))
        .map(_.toPath) ++ internalPluginClasspath
    val plugins = compilerPlugins(pluginClasspath, isDotty)
    plugins.map("-Xplugin:" + _.toAbsolutePath.toString).toSeq

  private lazy val internalCompilerPluginClasspath: Initialize[Task[Classpath]] =
    (Def
      .task { (thisProjectRef.value, settingsData.value, buildDependencies.value, streams.value) })
      .flatMapTask { case (ref, data, deps, s) =>
        ClasspathImpl.internalDependenciesImplTask(
          ref,
          CompilerPlugin,
          CompilerPlugin,
          data,
          deps,
          TrackLevel.TrackAlways,
          s.log
        )
      }

  lazy val compilerPluginConfig = Seq(
    scalacOptions := {
      given FileConverter = fileConverter.value
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
          .withFilter(_.getName == jarName)
          .map(f => (Artifact(f.getName.stripSuffix(".jar")), f))
        if (replaceWith.isEmpty) arts else replaceWith
      } else arts
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

  private def mavenCompatible(ivyRepo: xsbti.IvyRepository): Boolean =
    try {
      ivyRepo.mavenCompatible
    } catch { case _: NoSuchMethodError => false }

  private def skipConsistencyCheck(ivyRepo: xsbti.IvyRepository): Boolean =
    try {
      ivyRepo.skipConsistencyCheck
    } catch { case _: NoSuchMethodError => false }

  private def descriptorOptional(ivyRepo: xsbti.IvyRepository): Boolean =
    try {
      ivyRepo.descriptorOptional
    } catch { case _: NoSuchMethodError => false }

  // for forward-compatibility with launcher.jar prior to 1.3.11
  private def mavenRepoAllowInsecureProtocol(mavenRepo: xsbti.MavenRepository): Boolean =
    try {
      mavenRepo.allowInsecureProtocol
    } catch { case _: NoSuchMethodError => false }

  // for forward-compatibility with launcher.jar prior to 1.3.11
  private def allowInsecureProtocol(ivyRepo: xsbti.IvyRepository): Boolean =
    try {
      ivyRepo.allowInsecureProtocol
    } catch { case _: NoSuchMethodError => false }

  @nowarn
  private def bootRepository(repo: xsbti.Repository): Resolver = {
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
            Resolver.file(i.id, file)(using patterns)
          case _ =>
            Resolver
              .url(i.id, i.url)(using patterns)
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
  def shellPromptFromState(isColorEnabled: Boolean): State => String = { (s: State) =>
    val extracted = Project.extract(s)
    (extracted.currentRef / name).get(extracted.structure.data) match {
      case Some(name) =>
        s"sbt:$name" + Def.withColor(s"> ", Option(scala.Console.CYAN), isColorEnabled)
      case _ => "> "
    }
  }
}

private[sbt] object BuildExtra extends BuildExtra

trait BuildExtra extends BuildCommon with DefExtra {
  import Defaults._

  /**
   * Creates a new Project.  This is a macro that expects to be assigned directly to a val.
   * The name of the val is used as the project ID and the name of the base directory of the project.
   */
  inline def project: Project = ${ std.KeyMacro.projectImpl }
  inline def projectMatrix: ProjectMatrix = ${ ProjectMatrix.projectMatrixImpl }

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
   * Adds remote cache plugin.
   */
  def addRemoteCachePlugin: Setting[Seq[ModuleID]] =
    libraryDependencies += sbtPluginExtra(
      ModuleID("org.scala-sbt", "sbt-remote-cache", sbtVersion.value),
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
  def addArtifact(a: Artifact, taskDef: TaskKey[HashedVirtualFileRef]): SettingsDefinition = {
    val pkgd = packagedArtifacts := packagedArtifacts.value.updated(a, taskDef.value)
    Seq(artifacts += a, pkgd)
  }

  /** Constructs a setting that declares a new artifact `artifact` that is generated by `taskDef`. */
  def addArtifact(
      artifact: Initialize[Artifact],
      taskDef: Initialize[Task[HashedVirtualFileRef]]
  ): SettingsDefinition = {
    val artLocal = SettingKey.local[Artifact]
    val taskLocal = TaskKey.local[HashedVirtualFileRef]
    val art = artifacts := artLocal.value +: artifacts.value
    val pkgd = packagedArtifacts := packagedArtifacts.value.updated(artLocal.value, taskLocal.value)
    Seq(artLocal := artifact.value, taskLocal := taskDef.value, art, pkgd)
  }

  def runInputTask(
      config: Configuration,
      mainClass: String,
      baseArguments: String*
  ): Initialize[InputTask[Unit]] =
    Def.inputTask {
      given FileConverter = fileConverter.value
      import Def._
      val r = (config / run / runner).value
      val cp = (config / fullClasspath).value
      val args = spaceDelimited().parsed
      r.run(mainClass, cp.files, baseArguments ++ args, streams.value.log).get
    }

  def runTask(
      config: Configuration,
      mainClass: String,
      arguments: String*
  ): Initialize[Task[Unit]] =
    Def.task {
      given FileConverter = fileConverter.value
      val cp = (config / fullClasspath).value
      val r = (config / run / runner).value
      val s = streams.value
      r.run(mainClass, cp.files, arguments, s.log).get
    }

  // public API
  /** Returns a vector of settings that create custom run input task. */
  @nowarn
  def fullRunInputTask(
      scoped: InputKey[Unit],
      config: Configuration,
      mainClass: String,
      baseArguments: String*
  ): Vector[Setting[?]] = {
    Vector(
      scoped := Def
        .input(_ => Def.spaceDelimited())
        .flatMapTask { result =>
          initScoped(
            scoped.scopedKey,
            ClassLoaders.runner.mapReferenced(Project.mapScope(_.rescope(config))),
          ).zipWith(Def.task {
            ((config / fullClasspath).value, streams.value, fileConverter.value, result)
          }) { (rTask, t) =>
            (t, rTask) mapN { case ((cp, s, converter, args), r) =>
              given FileConverter = converter
              r.run(mainClass, cp.files, baseArguments ++ args, s.log).get
            }
          }
        }
        .evaluated
    ) ++ inTask(scoped)((config / forkOptions) := forkOptionsTask.value)
  }

  // public API
  /** Returns a vector of settings that create custom run task. */
  def fullRunTask(
      scoped: TaskKey[Unit],
      config: Configuration,
      mainClass: String,
      arguments: String*
  ): Vector[Setting[?]] =
    Vector(
      scoped := initScoped(
        scoped.scopedKey,
        ClassLoaders.runner.mapReferenced(Project.mapScope(_.rescope(config))),
      ).zipWith(Def.task { ((config / fullClasspath).value, streams.value, fileConverter.value) }) {
        case (rTask, t) =>
          (t, rTask).mapN { case ((cp, s, converter), r) =>
            given FileConverter = converter
            r.run(mainClass, cp.files, arguments, s.log).get
          }
      }.value
    ) ++ inTask(scoped)((config / forkOptions) := forkOptionsTask.value)

  def initScoped[T](sk: ScopedKey[?], i: Initialize[T]): Initialize[T] =
    initScope(fillTaskAxis(sk.scope, sk.key), i)
  def initScope[T](s: Scope, i: Initialize[T]): Initialize[T] =
    Project.inScope(s, i)

  /**
   * Disables post-compilation hook for determining tests for tab-completion (such as for 'test-only').
   * This is useful for reducing Test/compile time when not running test.
   */
  def noTestCompletion(config: Configuration = Test): Setting[?] =
    inConfig(config)(Seq(definedTests := detectTests.value)).head

  def filterKeys(ss: Seq[Setting[?]], transitive: Boolean = false)(
      f: ScopedKey[?] => Boolean
  ): Seq[Setting[?]] =
    ss filter (s => f(s.key) && (!transitive || s.dependencies.forall(f)))

  implicit def sbtStateToUpperStateOps(s: State): UpperStateOps =
    new UpperStateOps.UpperStateOpsImpl(s)
}

trait DefExtra {
  private val ts: TaskSequential = new TaskSequential {}
  implicit def toTaskSequential(@deprecated("unused", "") d: Def.type): TaskSequential = ts
}

trait BuildCommon {

  /**
   * Allows a String to be used where a `NameFilter` is expected.
   * Asterisks (`*`) in the string are interpreted as wildcards.
   * All other characters must match exactly.  See GlobFilter.
   */
  implicit def globFilter(expression: String): NameFilter = GlobFilter(expression)

  extension (s: PathFinder)
    def classpath(using FileConverter): Classpath =
      val converter = summon[FileConverter]
      Attributed.blankSeq(s.get().map(p => converter.toVirtualFile(p.toPath): HashedVirtualFileRef))

  extension (s: Classpath)
    def files(using FileConverter): Seq[NioPath] =
      val converter = summon[FileConverter]
      Attributed.data(s).map(converter.toPath)

  extension (s: Seq[HashedVirtualFileRef])
    /** Converts the `Seq[HashedVirtualFileRef]` to a Classpath, which is an alias for `Seq[Attributed[HashedVirtualFileRef]]`. */
    def classpath: Classpath = Attributed.blankSeq(s)

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
  def getFromContext[T](task: TaskKey[T], context: ScopedKey[?], s: State): Option[T] =
    SessionVar.get(SessionVar.resolveContext(task.scopedKey, context.scope, s), s)

  def loadFromContext[T](task: TaskKey[T], context: ScopedKey[?], s: State)(using
      f: JsonFormat[T]
  ): Option[T] =
    SessionVar.load(SessionVar.resolveContext(task.scopedKey, context.scope, s), s)

  // intended for use in constructing InputTasks
  def loadForParser[P, T](task: TaskKey[T])(
      f: (State, Option[T]) => Parser[P]
  )(using format: JsonFormat[T]): Initialize[State => Parser[P]] =
    loadForParserI(task)(Def.value(f))(using format)
  def loadForParserI[P, T](task: TaskKey[T])(
      init: Initialize[(State, Option[T]) => Parser[P]]
  )(using format: JsonFormat[T]): Initialize[State => Parser[P]] =
    Def.setting { (s: State) =>
      init.value(s, loadFromContext(task, resolvedScoped.value, s)(using format))
    }

  def getForParser[P, T](
      task: TaskKey[T]
  )(init: (State, Option[T]) => Parser[P]): Initialize[State => Parser[P]] =
    getForParserI(task)(Def.value(init))
  def getForParserI[P, T](
      task: TaskKey[T]
  )(init: Initialize[(State, Option[T]) => Parser[P]]): Initialize[State => Parser[P]] =
    Def.setting { (s: State) =>
      init.value(s, getFromContext(task, resolvedScoped.value, s))
    }

  // these are for use for constructing Tasks
  def loadPrevious[T](task: TaskKey[T])(using f: JsonFormat[T]): Initialize[Task[Option[T]]] =
    Def.task { loadFromContext(task, resolvedScoped.value, state.value)(using f) }
  def getPrevious[A](task: TaskKey[A]): Initialize[Task[Option[A]]] =
    Def.task { getFromContext(task, resolvedScoped.value, state.value) }

  private[sbt] def derive[T](s: Setting[T]): Setting[T] =
    Def.derive(s, allowDynamic = true, trigger = _ != streams.key, default = true)
}
