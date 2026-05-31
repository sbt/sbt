/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.util.LinkedHashSet
import sbt.Keys.*
import sbt.nio.Keys.*
import sbt.nio.file.{ Glob, RecursiveGlob }
import sbt.Def.Initialize
import sbt.internal.util.{ Attributed, Dag }
import sbt.librarymanagement.{
  ConfigRef,
  Configuration,
  CrossVersion,
  DependencyMode,
  ModuleID,
  ScalaArtifacts,
  TrackLevel,
  UpdateReport
}
import sbt.librarymanagement.Configurations.names
import sbt.SlashSyntax0.*
import sbt.std.TaskExtra.*
import sbt.util.*
import scala.jdk.CollectionConverters.*
import xsbti.{ HashedVirtualFileRef, VirtualFile, VirtualFileRef }

private[sbt] object ClasspathImpl {

  // Since we can't predict the path for pickleProduct,
  // we can't reduce the track level.
  def exportedPicklesTask: Initialize[Task[Classpath]] =
    Def.task {
      // conditional task: do not refactor
      if exportPipelining.value then
        val module = projectID.value
        val config = configuration.value
        val products = pickleProducts.value
        val analysis = compileEarly.value
        val converter = fileConverter.value
        val analysisFile = converter.toVirtualFile(earlyCompileAnalysisFile.value.toPath)

        val xs = products.map(_ -> analysis)
        for (f, analysis) <- xs
        yield APIMappings
          .store(Classpaths.analyzed(f, analysisFile), apiURL.value)
          .put(Keys.moduleIDStr, Classpaths.moduleIdJsonKeyFormat.write(module))
          .put(Keys.configurationStr, config.name)
      else exportedProducts.value
    }

  def trackedExportedProducts(track: TrackLevel): Initialize[Task[Classpath]] =
    Def.task {
      val _ = (packageBin / dynamicDependency).value
      val art = (packageBin / artifact).value
      val module = projectID.value
      val config = configuration.value
      for (f, analysis) <- trackedExportedProductsImplTask(track).value
      yield APIMappings
        .store(Classpaths.analyzed(f, analysis), apiURL.value)
        .put(Keys.artifactStr, RemoteCache.artifactToStr(art))
        .put(Keys.moduleIDStr, Classpaths.moduleIdJsonKeyFormat.write(module))
        .put(Keys.configurationStr, config.name)
    }

  def trackedExportedJarProducts(track: TrackLevel): Initialize[Task[Classpath]] =
    Def.task {
      val _ = (packageBin / dynamicDependency).value
      val art = (packageBin / artifact).value
      val module = projectID.value
      val config = configuration.value
      for (f, analysis) <- trackedJarProductsImplTask(track).value
      yield APIMappings
        .store(Classpaths.analyzed(f, analysis), apiURL.value)
        .put(Keys.artifactStr, RemoteCache.artifactToStr(art))
        .put(Keys.moduleIDStr, Classpaths.moduleIdJsonKeyFormat.write(module))
        .put(Keys.configurationStr, config.name)
    }

  private def trackedExportedProductsImplTask(
      track: TrackLevel
  ): Initialize[Task[Seq[(HashedVirtualFileRef, VirtualFile)]]] =
    Def.taskIf {
      if {
        val _ = (packageBin / dynamicDependency).value
        exportJars.value
      } then trackedJarProductsImplTask(track).value
      else trackedNonJarProductsImplTask(track).value
    }

  private def trackedNonJarProductsImplTask(
      track: TrackLevel
  ): Initialize[Task[Seq[(HashedVirtualFileRef, VirtualFile)]]] =
    Def
      .task {
        val dirs = productDirectories.value
        val view = fileTreeView.value
        (TrackLevel.intersection(track, exportToInternal.value), dirs, view)
      }
      .flatMapTask {
        case (TrackLevel.TrackAlways, _, _) =>
          Def.task {
            val converter = fileConverter.value
            val analysisFile = converter.toVirtualFile(compileAnalysisFile.value.toPath)
            products.value.map(x => (converter.toVirtualFile(x.toPath()), analysisFile))
          }
        case (TrackLevel.TrackIfMissing, dirs, view)
            if view.list(dirs.map(Glob(_, RecursiveGlob / "*.class"))).isEmpty =>
          Def.task {
            val converter = fileConverter.value
            val analysisFile = converter.toVirtualFile(compileAnalysisFile.value.toPath)
            products.value.map(x => (converter.toVirtualFile(x.toPath()), analysisFile))
          }
        case (_, dirs, _) =>
          Def.task {
            val converter = fileConverter.value
            val analysisFile = converter.toVirtualFile(compileAnalysisFile.value.toPath)
            dirs.map { x => (converter.toVirtualFile(x.toPath()), analysisFile) }
          }
      }

  private def trackedJarProductsImplTask(
      track: TrackLevel
  ): Initialize[Task[Seq[(HashedVirtualFileRef, VirtualFile)]]] =
    (Def
      .task {
        val converter = fileConverter.value
        val vf = (packageBin / artifactPath).value
        val jar = converter.toPath(vf)
        (TrackLevel.intersection(track, exportToInternal.value), vf, jar)
      })
      .flatMapTask {
        case (TrackLevel.TrackAlways, _, _) =>
          Def.task {
            val converter = fileConverter.value
            val analysisFile = converter.toVirtualFile(compileAnalysisFile.value.toPath)
            Seq((packageBin.value, analysisFile))
          }
        case (TrackLevel.TrackIfMissing, _, jar) if !jar.toFile().exists =>
          Def.task {
            val converter = fileConverter.value
            val analysisFile = converter.toVirtualFile(compileAnalysisFile.value.toPath)
            Seq((packageBin.value, analysisFile))
          }
        case (_, vf, _) =>
          Def.task {
            val converter = fileConverter.value
            val analysisFile = converter.toVirtualFile(compileAnalysisFile.value.toPath)
            Seq(vf).map { x => (converter.toVirtualFile(x), analysisFile) }
          }
      }

  def internalDependencyClasspathTask: Initialize[Task[Classpath]] =
    (Def
      .task {
        val _ = (
          (exportedProductsNoTracking / transitiveClasspathDependency).value,
          (exportedProductsIfMissing / transitiveClasspathDependency).value,
          (exportedProducts / transitiveClasspathDependency).value,
          (exportedProductJarsNoTracking / transitiveClasspathDependency).value,
          (exportedProductJarsIfMissing / transitiveClasspathDependency).value,
          (exportedProductJars / transitiveClasspathDependency).value
        )
      })
      .flatMapTask { case u =>
        Def.task {
          (
            thisProjectRef.value,
            classpathConfiguration.value,
            configuration.value,
            settingsData.value,
            buildDependencies.value,
            trackInternalDependencies.value,
            streams.value.log,
          )
        }
      }
      .flatMapTask { internalDependenciesImplTask }

  def internalDependenciesImplTask(
      projectRef: ProjectRef,
      conf: Configuration,
      self: Configuration,
      data: Def.Settings,
      deps: BuildDependencies,
      track: TrackLevel,
      log: Logger
  ): Initialize[Task[Classpath]] =
    Def.value[Task[Classpath]] {
      interDependencies(projectRef, deps, conf, self, data, track, false, log)(
        exportedProductsNoTracking,
        exportedProductsIfMissing,
        exportedProducts
      )
    }

  def internalDependencyPicklePathTask: Initialize[Task[Classpath]] = {
    def implTask(
        projectRef: ProjectRef,
        conf: Configuration,
        self: Configuration,
        data: Def.Settings,
        deps: BuildDependencies,
        track: TrackLevel,
        log: Logger
    ): Initialize[Task[Classpath]] =
      Def.value[Task[Classpath]] {
        interDependencies(projectRef, deps, conf, self, data, track, false, log)(
          exportedPickles,
          exportedPickles,
          exportedPickles
        )
      }
    (Def
      .task {
        (
          thisProjectRef.value,
          classpathConfiguration.value,
          configuration.value,
          settingsData.value,
          buildDependencies.value,
          TrackLevel.TrackAlways,
          streams.value.log,
        )
      })
      .flatMapTask(implTask)
  }

  def internalDependencyJarsTask: Initialize[Task[Classpath]] =
    (Def
      .task {
        (
          thisProjectRef.value,
          classpathConfiguration.value,
          configuration.value,
          settingsData.value,
          buildDependencies.value,
          trackInternalDependencies.value,
          streams.value.log,
        )
      })
      .flatMapTask(internalDependencyJarsImplTask)

  private def internalDependencyJarsImplTask(
      projectRef: ProjectRef,
      conf: Configuration,
      self: Configuration,
      data: Def.Settings,
      deps: BuildDependencies,
      track: TrackLevel,
      log: Logger
  ): Initialize[Task[Classpath]] =
    Def.value[Task[Classpath]] {
      interDependencies[Attributed[HashedVirtualFileRef]](
        projectRef,
        deps,
        conf,
        self,
        data,
        track,
        false,
        log,
      )(
        exportedProductJarsNoTracking,
        exportedProductJarsIfMissing,
        exportedProductJars
      ): Task[Classpath]
    }

  def unmanagedDependenciesTask: Initialize[Task[Classpath]] =
    (Def
      .task {
        (
          thisProjectRef.value,
          configuration.value,
          settingsData.value,
          buildDependencies.value,
          streams.value.log
        )
      })
      .flatMapTask(unmanagedDependencies0)

  def unmanagedDependencies0(
      projectRef: ProjectRef,
      conf: Configuration,
      data: Def.Settings,
      deps: BuildDependencies,
      log: Logger
  ): Initialize[Task[Classpath]] =
    Def.value[Task[Classpath]] {
      interDependencies(
        projectRef,
        deps,
        conf,
        conf,
        data,
        TrackLevel.TrackAlways,
        true,
        log
      )(
        unmanagedJars,
        unmanagedJars,
        unmanagedJars
      )
    }

  def unmanagedLibs(
      dep: ResolvedReference,
      conf: String,
      data: Def.Settings
  ): Task[Classpath] =
    getClasspath(unmanagedJars, dep, conf, data)

  private[sbt] def isAllowedScalaMismatch(sv1: String, sv2: String): Boolean =
    val pv1 = CrossVersion.partialVersion(sv1)
    val pv2 = CrossVersion.partialVersion(sv2)
    (pv1, pv2) match
      case (Some((2, 13)), Some((3, minor))) => minor <= 7
      case (Some((3, minor)), Some((2, 13))) => minor <= 7
      case _                                 => false

  def interDependencies[A](
      projectRef: ProjectRef,
      deps: BuildDependencies,
      conf: Configuration,
      self: Configuration,
      data: Def.Settings,
      track: TrackLevel,
      includeSelf: Boolean,
      log: Logger
  )(
      noTracking: TaskKey[Seq[A]],
      trackIfMissing: TaskKey[Seq[A]],
      trackAlways: TaskKey[Seq[A]]
  ): Task[Seq[A]] = {
    val interDepConfigs = interSort(projectRef, conf, data, deps) filter { (dep, c) =>
      includeSelf || (dep != projectRef) || (conf.name != c && self.name != c)
    }
    val tasks = (new LinkedHashSet[Task[Seq[A]]]).asScala
    for {
      (dep, c) <- interDepConfigs
    } {
      tasks += (track match {
        case TrackLevel.NoTracking =>
          getClasspath(noTracking, dep, c, data)
        case TrackLevel.TrackIfMissing =>
          getClasspath(trackIfMissing, dep, c, data)
        case TrackLevel.TrackAlways =>
          getClasspath(trackAlways, dep, c, data)
      })
    }
    (tasks.toSeq.join).map(_.flatten.distinct)
  }

  def interSort(
      projectRef: ProjectRef,
      conf: Configuration,
      data: Def.Settings,
      deps: BuildDependencies
  ): Seq[(ProjectRef, String)] =
    val visited = (new LinkedHashSet[(ProjectRef, String)]).asScala
    def visit(p: ProjectRef, c: Configuration): Unit =
      val applicableConfigs = allConfigs(c)
      for ac <- applicableConfigs do
        // add all configurations in this project
        visited add (p -> ac.name)
        val masterConfs = names(getConfigurations(projectRef, data).toVector)

        for case ClasspathDep.ResolvedClasspathDependency(dep, confMapping) <- deps.classpath(p) do
          val configurations = getConfigurations(dep, data)
          val mapping =
            mapped(
              confMapping,
              masterConfs,
              names(configurations.toVector),
              "compile",
              "*->compile"
            )
          // map master configuration 'c' and all extended configurations to the appropriate dependency configuration
          for
            ac <- applicableConfigs
            depConfName <- mapping(ac.name)
          do
            for depConf <- confOpt(configurations, depConfName) do
              if !visited((dep, depConfName)) then visit(dep, depConf)
    visit(projectRef, conf)
    visited.toSeq
  end interSort

  def mapped(
      confString: Option[String],
      masterConfs: Seq[String],
      depConfs: Seq[String],
      default: String,
      defaultMapping: String
  ): String => Seq[String] = {
    lazy val defaultMap = parseMapping(defaultMapping, masterConfs, depConfs, _ :: Nil)
    parseMapping(confString getOrElse default, masterConfs, depConfs, defaultMap)
  }

  def parseMapping(
      confString: String,
      masterConfs: Seq[String],
      depConfs: Seq[String],
      default: String => Seq[String]
  ): String => Seq[String] =
    union(confString.split(";").map(parseSingleMapping(masterConfs, depConfs, default)).toSeq)

  def parseSingleMapping(
      masterConfs: Seq[String],
      depConfs: Seq[String],
      default: String => Seq[String]
  )(confString: String): String => Seq[String] = {
    val ms: Seq[(String, Seq[String])] =
      trim(confString.split("->", 2)) match {
        case x :: Nil      => for (a <- parseList(x, masterConfs)) yield (a, default(a))
        case x :: y :: Nil =>
          val target = parseList(y, depConfs);
          for (a <- parseList(x, masterConfs)) yield (a, target)
        case _ => sys.error("Invalid configuration '" + confString + "'") // shouldn't get here
      }
    val m = ms.toMap
    s => m.getOrElse(s, Nil)
  }

  def union[A, B](maps: Seq[A => Seq[B]]): A => Seq[B] =
    a => maps.foldLeft(Seq[B]()) { _ ++ _(a) }.distinct

  def parseList(s: String, allConfs: Seq[String]): Seq[String] =
    trim(s.split(",")).flatMap(replaceWildcard(allConfs)).distinct

  def replaceWildcard(allConfs: Seq[String])(conf: String): Seq[String] = conf match {
    case ""  => Nil
    case "*" => allConfs
    case _   => conf :: Nil
  }

  private def trim(a: Array[String]): List[String] = a.toList.map(_.trim)

  def allConfigs(conf: Configuration): Seq[Configuration] =
    Dag.reverseTopologicalSort(conf)(_.extendsConfigs)

  def getConfigurations(p: ResolvedReference, data: Def.Settings): Seq[Configuration] =
    (p / ivyConfigurations).get(data).getOrElse(Nil)

  def confOpt(configurations: Seq[Configuration], conf: String): Option[Configuration] =
    configurations.find(_.name == conf)

  def getClasspath[A](
      key: TaskKey[Seq[A]],
      dep: ResolvedReference,
      conf: Configuration,
      data: Def.Settings
  ): Task[Seq[A]] = getClasspath(key, dep, conf.name, data)

  def getClasspath[A](
      key: TaskKey[Seq[A]],
      dep: ResolvedReference,
      conf: String,
      data: Def.Settings
  ): Task[Seq[A]] =
    (dep / ConfigKey(conf) / key).get(data) match {
      case Some(x) => x
      case _       => constant(Nil)
    }

  // -- dependencyMode filtering --

  private def isScalaLibraryModule(mid: ModuleID): Boolean =
    mid.organization == ScalaArtifacts.Organization &&
      (mid.name == ScalaArtifacts.LibraryID ||
        mid.name == ScalaArtifacts.Scala3LibraryID ||
        mid.name.startsWith(ScalaArtifacts.Scala3LibraryPrefix))

  /** Build a lookup from org -> Set[baseName] for cross-version aware matching. */
  private def directDepIndex(
      directDeps: Seq[ModuleID],
  ): Map[String, Set[String]] =
    directDeps.groupMap(_.organization)(_.name).map((k, v) => k -> v.toSet)

  /** Check if a resolved module matches any direct dep, accounting for cross-version suffixes. */
  private def matchesDirectDep(
      mid: ModuleID,
      index: Map[String, Set[String]],
  ): Boolean =
    index.get(mid.organization) match
      case None        => false
      case Some(names) =>
        names.exists(n => mid.name == n || mid.name.startsWith(n + "_"))

  def filterByDirectDeps(
      directDeps: Seq[ModuleID],
      jars: Classpath,
  ): Classpath =
    val index = directDepIndex(directDeps)
    jars.filter: entry =>
      entry.get(Keys.moduleIDStr) match
        case Some(str) =>
          val mid = Classpaths.moduleIdJsonKeyFormat.read(str)
          matchesDirectDep(mid, index) || isScalaLibraryModule(mid)
        case None => true

  def filterByPlusOne(
      directDeps: Seq[ModuleID],
      projectId: ModuleID,
      config: Configuration,
      fullReport: UpdateReport,
      jars: Classpath,
  ): Classpath =
    val index = directDepIndex(directDeps)
    val rootKey = (projectId.organization, projectId.name)
    fullReport.configuration(ConfigRef(config.name)) match
      case None               => jars
      case Some(configReport) =>
        val modules = configReport.modules
        // Callers use resolved names (e.g., cats-core_3).
        // Build the set of resolved direct dep keys from the full report.
        val resolvedDirectKeys: Set[(String, String)] = modules
          .filter(mr => matchesDirectDep(mr.module, index))
          .map(mr => (mr.module.organization, mr.module.name))
          .toSet
        val plusOneKeys: Set[(String, String)] = modules
          .filter: mr =>
            mr.callers.exists: c =>
              val ck = (c.caller.organization, c.caller.name)
              resolvedDirectKeys.contains(ck) || ck == rootKey
          .map(mr => (mr.module.organization, mr.module.name))
          .toSet
        val allowedKeys = resolvedDirectKeys ++ plusOneKeys
        jars.filter: entry =>
          entry.get(Keys.moduleIDStr) match
            case Some(str) =>
              val mid = Classpaths.moduleIdJsonKeyFormat.read(str)
              allowedKeys.contains((mid.organization, mid.name)) ||
              isScalaLibraryModule(mid)
            case None => true

  /**
   * Apply dependencyMode filtering to a classpath. Entries without moduleIDStr metadata
   * (e.g. internal project outputs) pass through unchanged.
   */
  def filterByDependencyMode(
      mode: DependencyMode,
      directDeps: Seq[ModuleID],
      projectId: ModuleID,
      config: Configuration,
      fullReport: UpdateReport,
      cp: Classpath,
  ): Classpath =
    mode match
      case DependencyMode.Transitive => cp
      case DependencyMode.Direct     => filterByDirectDeps(directDeps, cp)
      case DependencyMode.PlusOne => filterByPlusOne(directDeps, projectId, config, fullReport, cp)

  /**
   * Apply dependencyMode filtering to the *internal* classpath using the build's project graph.
   * `UpdateReport` only contains externally resolved modules, so it cannot answer
   * "is this internal project a direct dep of `projectRef`"; the `BuildDependencies` graph can.
   *
   *   Direct  -- entries from projects directly listed in `projectRef.dependsOn(...)`.
   *   PlusOne -- direct + one more hop along the project graph.
   *   Transitive -- unfiltered.
   */
  def filterInternalByMode(
      mode: DependencyMode,
      projectRef: ProjectRef,
      data: Def.Settings,
      deps: BuildDependencies,
      internalCp: Classpath,
  ): Classpath =
    mode match
      case DependencyMode.Transitive => internalCp
      case _                         =>
        val allowed = allowedInternalKeys(mode, projectRef, data, deps)
        internalCp.filter: entry =>
          entry.get(Keys.moduleIDStr) match
            case Some(str) =>
              val mid = Classpaths.moduleIdJsonKeyFormat.read(str)
              allowed.contains((mid.organization, mid.name))
            case None => true

  private def allowedInternalKeys(
      mode: DependencyMode,
      projectRef: ProjectRef,
      data: Def.Settings,
      deps: BuildDependencies,
  ): Set[(String, String)] =
    def directRefs(p: ProjectRef): Set[ProjectRef] =
      deps
        .classpath(p)
        .collect:
          case ClasspathDep.ResolvedClasspathDependency(dep, _) => dep
        .toSet
    val refs: Set[ProjectRef] = mode match
      case DependencyMode.Direct =>
        directRefs(projectRef)
      case DependencyMode.PlusOne =>
        val direct = directRefs(projectRef)
        direct ++ direct.flatMap(directRefs)
      case DependencyMode.Transitive => Set.empty
    refs.flatMap: pr =>
      (pr / projectID).get(data).map(mid => (mid.organization, mid.name))

}
