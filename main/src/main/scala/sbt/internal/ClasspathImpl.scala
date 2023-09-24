/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.io.File
import java.util.LinkedHashSet
import sbt.SlashSyntax0._
import sbt.Keys._
import sbt.nio.Keys._
import sbt.nio.file.{ Glob, RecursiveGlob }
import sbt.Def.Initialize
import sbt.internal.inc.Analysis
import sbt.internal.inc.JavaInterfaceUtil._
import sbt.internal.util.{ Attributed, Dag, Settings }
import sbt.librarymanagement.{ Configuration, TrackLevel }
import sbt.librarymanagement.Configurations.names
import sbt.std.TaskExtra._
import sbt.util._
import scala.collection.JavaConverters._
import xsbti.compile.CompileAnalysis

private[sbt] object ClasspathImpl {

  // Since we can't predict the path for pickleProduct,
  // we can't reduce the track level.
  def exportedPicklesTask: Initialize[Task[VirtualClasspath]] =
    Def.task {
      // conditional task: do not refactor
      if (exportPipelining.value) {
        val module = projectID.value
        val config = configuration.value
        val products = pickleProducts.value
        val analysis = compileEarly.value
        val xs = products map { _ -> analysis }
        for { (f, analysis) <- xs } yield APIMappings
          .store(analyzed(f, analysis), apiURL.value)
          .put(Keys.moduleIDStr, Classpaths.moduleIdJsonKeyFormat.write(module))
          .put(Keys.configurationStr, config.name)
      } else {
        val c = fileConverter.value
        val ps = exportedProducts.value
        ps.map(attr => attr.map(x => c.toVirtualFile(x.toPath)))
      }
    }

  def trackedExportedProducts(track: TrackLevel): Initialize[Task[Classpath]] =
    Def.task {
      val _ = (packageBin / dynamicDependency).value
      val art = (packageBin / artifact).value
      val module = projectID.value
      val config = configuration.value
      for { (f, analysis) <- trackedExportedProductsImplTask(track).value } yield APIMappings
        .store(analyzed(f, analysis), apiURL.value)
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
      for { (f, analysis) <- trackedJarProductsImplTask(track).value } yield APIMappings
        .store(analyzed(f, analysis), apiURL.value)
        .put(Keys.artifactStr, RemoteCache.artifactToStr(art))
        .put(Keys.moduleIDStr, Classpaths.moduleIdJsonKeyFormat.write(module))
        .put(Keys.configurationStr, config.name)
    }

  private[this] def trackedExportedProductsImplTask(
      track: TrackLevel
  ): Initialize[Task[Seq[(File, CompileAnalysis)]]] =
    Def.taskIf {
      if {
        val _ = (packageBin / dynamicDependency).value
        exportJars.value
      } then trackedJarProductsImplTask(track).value
      else trackedNonJarProductsImplTask(track).value
    }

  private[this] def trackedNonJarProductsImplTask(
      track: TrackLevel
  ): Initialize[Task[Seq[(File, CompileAnalysis)]]] =
    (Def
      .task {
        val dirs = productDirectories.value
        val view = fileTreeView.value
        (TrackLevel.intersection(track, exportToInternal.value), dirs, view)
      })
      .flatMapTask {
        case (TrackLevel.TrackAlways, _, _) =>
          Def.task {
            products.value map { (_, compile.value) }
          }
        case (TrackLevel.TrackIfMissing, dirs, view)
            if view.list(dirs.map(Glob(_, RecursiveGlob / "*.class"))).isEmpty =>
          Def.task {
            products.value map { (_, compile.value) }
          }
        case (_, dirs, _) =>
          Def.task {
            val analysis = previousCompile.value.analysis.toOption.getOrElse(Analysis.empty)
            dirs.map(_ -> analysis)
          }
      }

  private[this] def trackedJarProductsImplTask(
      track: TrackLevel
  ): Initialize[Task[Seq[(File, CompileAnalysis)]]] =
    (Def
      .task {
        val jar = (packageBin / artifactPath).value
        (TrackLevel.intersection(track, exportToInternal.value), jar)
      })
      .flatMapTask {
        case (TrackLevel.TrackAlways, _) =>
          Def.task {
            Seq((packageBin.value, compile.value))
          }
        case (TrackLevel.TrackIfMissing, jar) if !jar.exists =>
          Def.task {
            Seq((packageBin.value, compile.value))
          }
        case (_, jar) =>
          Def.task {
            val analysisOpt = previousCompile.value.analysis.toOption
            Seq(jar) map { x =>
              (
                x,
                if (analysisOpt.isDefined) analysisOpt.get
                else Analysis.empty
              )
            }
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
      data: Settings[Scope],
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

  def internalDependencyPicklePathTask: Initialize[Task[VirtualClasspath]] = {
    def implTask(
        projectRef: ProjectRef,
        conf: Configuration,
        self: Configuration,
        data: Settings[Scope],
        deps: BuildDependencies,
        track: TrackLevel,
        log: Logger
    ): Initialize[Task[VirtualClasspath]] =
      Def.value[Task[VirtualClasspath]] {
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
      data: Settings[Scope],
      deps: BuildDependencies,
      track: TrackLevel,
      log: Logger
  ): Initialize[Task[Classpath]] =
    Def.value[Task[Classpath]] {
      interDependencies(projectRef, deps, conf, self, data, track, false, log)(
        exportedProductJarsNoTracking,
        exportedProductJarsIfMissing,
        exportedProductJars
      )
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
      data: Settings[Scope],
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
      data: Settings[Scope]
  ): Task[Classpath] =
    getClasspath(unmanagedJars, dep, conf, data)

  def interDependencies[A](
      projectRef: ProjectRef,
      deps: BuildDependencies,
      conf: Configuration,
      self: Configuration,
      data: Settings[Scope],
      track: TrackLevel,
      includeSelf: Boolean,
      log: Logger
  )(
      noTracking: TaskKey[Seq[A]],
      trackIfMissing: TaskKey[Seq[A]],
      trackAlways: TaskKey[Seq[A]]
  ): Task[Seq[A]] = {
    val interDepConfigs = interSort(projectRef, conf, data, deps) filter { case (dep, c) =>
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

  def analyzed[A](data: A, analysis: CompileAnalysis) =
    RemoteCache.postAnalysis(analysis) match
      case Some(ref) =>
        Attributed
          .blank(data)
          .put(Keys.analysis, CacheImplicits.hashedVirtualFileRefToStr(ref))
      case None => Attributed.blank(data)

  def interSort(
      projectRef: ProjectRef,
      conf: Configuration,
      data: Settings[Scope],
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
    union(confString.split(";") map parseSingleMapping(masterConfs, depConfs, default))

  def parseSingleMapping(
      masterConfs: Seq[String],
      depConfs: Seq[String],
      default: String => Seq[String]
  )(confString: String): String => Seq[String] = {
    val ms: Seq[(String, Seq[String])] =
      trim(confString.split("->", 2)) match {
        case x :: Nil => for (a <- parseList(x, masterConfs)) yield (a, default(a))
        case x :: y :: Nil =>
          val target = parseList(y, depConfs);
          for (a <- parseList(x, masterConfs)) yield (a, target)
        case _ => sys.error("Invalid configuration '" + confString + "'") // shouldn't get here
      }
    val m = ms.toMap
    s => m.getOrElse(s, Nil)
  }

  def union[A, B](maps: Seq[A => Seq[B]]): A => Seq[B] =
    a => maps.foldLeft(Seq[B]()) { _ ++ _(a) } distinct;

  def parseList(s: String, allConfs: Seq[String]): Seq[String] =
    (trim(s split ",") flatMap replaceWildcard(allConfs)).distinct

  def replaceWildcard(allConfs: Seq[String])(conf: String): Seq[String] = conf match {
    case ""  => Nil
    case "*" => allConfs
    case _   => conf :: Nil
  }

  private def trim(a: Array[String]): List[String] = a.toList.map(_.trim)

  def allConfigs(conf: Configuration): Seq[Configuration] =
    Dag.topologicalSort(conf)(_.extendsConfigs)

  def getConfigurations(p: ResolvedReference, data: Settings[Scope]): Seq[Configuration] =
    (p / ivyConfigurations).get(data).getOrElse(Nil)

  def confOpt(configurations: Seq[Configuration], conf: String): Option[Configuration] =
    configurations.find(_.name == conf)

  def getClasspath[A](
      key: TaskKey[Seq[A]],
      dep: ResolvedReference,
      conf: Configuration,
      data: Settings[Scope]
  ): Task[Seq[A]] = getClasspath(key, dep, conf.name, data)

  def getClasspath[A](
      key: TaskKey[Seq[A]],
      dep: ResolvedReference,
      conf: String,
      data: Settings[Scope]
  ): Task[Seq[A]] =
    (dep / ConfigKey(conf) / key).get(data) match {
      case Some(x) => x
      case _       => constant(Nil)
    }

}
