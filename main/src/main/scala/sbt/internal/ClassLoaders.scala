/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.io.File
import java.nio.file.Path

import sbt.ClassLoaderLayeringStrategy._
import sbt.Keys._
import sbt.internal.classpath.ClassLoaderCache
import sbt.internal.inc.ScalaInstance
import sbt.internal.inc.classpath.ClasspathUtilities
import sbt.internal.util.Attributed
import sbt.internal.util.Attributed.data
import sbt.io.IO
import sbt.nio.FileStamp
import sbt.nio.FileStamp.LastModified
import sbt.nio.Keys._

private[sbt] object ClassLoaders {
  private[this] val interfaceLoader = classOf[sbt.testing.Framework].getClassLoader
  /*
   * Get the class loader for a test task. The configuration could be IntegrationTest or Test.
   */
  private[sbt] def testTask: Def.Initialize[Task[ClassLoader]] = Def.task {
    val si = scalaInstance.value
    val cp = fullClasspath.value.map(_.data)
    val dependencyStamps = modifiedTimes((outputFileStamps in dependencyClasspathFiles).value).toMap
    def getLm(f: File): Long = dependencyStamps.getOrElse(f, IO.getModifiedTimeOrZero(f))
    val rawCP = cp.map(f => f -> getLm(f))
    val fullCP =
      if (si.isManagedVersion) rawCP
      else si.libraryJars.map(j => j -> IO.getModifiedTimeOrZero(j)).toSeq ++ rawCP
    val exclude = dependencyJars(exportedProducts).value.toSet ++ si.libraryJars
    buildLayers(
      strategy = classLoaderLayeringStrategy.value,
      si = si,
      fullCP = fullCP,
      allDependenciesSet = dependencyJars(dependencyClasspath).value.filterNot(exclude).toSet,
      cache = extendedClassLoaderCache.value,
      resources = ClasspathUtilities.createClasspathResources(fullCP.map(_._1), si),
      tmp = IO.createUniqueDirectory(taskTemporaryDirectory.value),
      scope = resolvedScoped.value.scope
    )
  }

  private[sbt] def runner: Def.Initialize[Task[ScalaRun]] = Def.taskDyn {
    val resolvedScope = resolvedScoped.value.scope
    val instance = scalaInstance.value
    val s = streams.value
    val opts = forkOptions.value
    val options = javaOptions.value
    if (fork.value) {
      s.log.debug(s"javaOptions: $options")
      Def.task(new ForkRun(opts))
    } else {
      Def.task {
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
        val exclude = dependencyJars(exportedProducts).value.toSet ++ instance.libraryJars
        val allDeps = dependencyJars(dependencyClasspath).value.filterNot(exclude)
        val newLoader =
          (classpath: Seq[File]) => {
            val mappings = classpath.map(f => f.getName -> f).toMap
            val transformedDependencies = allDeps.map(f => mappings.getOrElse(f.getName, f))
            buildLayers(
              strategy = classLoaderLayeringStrategy.value: @sbtUnchecked,
              si = instance,
              fullCP = classpath.map(f => f -> IO.getModifiedTimeOrZero(f)),
              allDependenciesSet = transformedDependencies.toSet,
              cache = extendedClassLoaderCache.value: @sbtUnchecked,
              resources = ClasspathUtilities.createClasspathResources(classpath, instance),
              tmp = taskTemporaryDirectory.value: @sbtUnchecked,
              scope = resolvedScope
            )
          }
        new Run(newLoader, trapExit.value)
      }
    }
  }

  private[this] def extendedClassLoaderCache: Def.Initialize[Task[ClassLoaderCache]] = Def.task {
    val errorMessage = "Tried to extract classloader cache for uninitialized state."
    state.value
      .get(BasicKeys.extendedClassLoaderCache)
      .getOrElse(throw new IllegalStateException(errorMessage))
  }
  /*
   * Create a layered classloader. There are up to four layers:
   * 1) the scala instance class loader (may actually be two layers if scala-reflect is used)
   * 2) the resource layer
   * 3) the dependency jars
   * 4) the rest of the classpath
   * The first three layers may be optionally cached to reduce memory usage and improve
   * start up latency. Because there may be mutually incompatible libraries in the runtime
   * and test dependencies, it's important to be able to configure which layers are used.
   */
  private def buildLayers(
      strategy: ClassLoaderLayeringStrategy,
      si: ScalaInstance,
      fullCP: Seq[(File, Long)],
      allDependenciesSet: Set[File],
      cache: ClassLoaderCache,
      resources: Map[String, String],
      tmp: File,
      scope: Scope
  ): ClassLoader = {
    val cpFiles = fullCP.map(_._1)
    strategy match {
      case Flat => flatLoader(cpFiles, interfaceLoader)
      case _ =>
        val layerDependencies = strategy match {
          case _: AllLibraryJars => true
          case _                 => false
        }
        val scalaLibraryLayer = {
          cache.apply(
            si.libraryJars.map(j => j -> IO.getModifiedTimeOrZero(j)).toList,
            interfaceLoader,
            () => new ScalaLibraryClassLoader(si.libraryJars.map(_.toURI.toURL), interfaceLoader)
          )
        }
        val cpFiles = fullCP.map(_._1)

        val allDependencies = cpFiles.filter(allDependenciesSet)
        val scalaReflectJar = allDependencies.collectFirst {
          case f if f.getName == "scala-reflect.jar" =>
            si.allJars.find(_.getName == "scala-reflect.jar")
        }.flatten
        val scalaReflectLayer = scalaReflectJar
          .map { file =>
            cache.apply(
              file -> IO.getModifiedTimeOrZero(file) :: Nil,
              scalaLibraryLayer,
              () => new ScalaReflectClassLoader(file.toURI.toURL, scalaLibraryLayer)
            )
          }
          .getOrElse(scalaLibraryLayer)

        // layer 2 (optional if in the test config and the runtime layer is not shared)
        val dependencyLayer: ClassLoader =
          if (layerDependencies && allDependencies.nonEmpty) {
            cache(
              allDependencies.toList.map(f => f -> IO.getModifiedTimeOrZero(f)),
              scalaReflectLayer,
              () => new ReverseLookupClassLoaderHolder(allDependencies, scalaReflectLayer)
            )
          } else scalaReflectLayer

        val scalaJarNames = (si.libraryJars ++ scalaReflectJar).map(_.getName).toSet
        // layer 3
        val filteredSet =
          if (layerDependencies) allDependencies.toSet ++ si.libraryJars ++ scalaReflectJar
          else Set(si.libraryJars ++ scalaReflectJar: _*)
        val dynamicClasspath = cpFiles.filterNot(f => filteredSet(f) || scalaJarNames(f.getName))
        dependencyLayer match {
          case dl: ReverseLookupClassLoaderHolder =>
            dl.checkout(cpFiles, tmp)
          case cl =>
            cl.getParent match {
              case dl: ReverseLookupClassLoaderHolder => dl.checkout(cpFiles, tmp)
              case _                                  => new LayeredClassLoader(dynamicClasspath, cl, tmp)
            }
        }
    }
  }

  private def dependencyJars(
      key: sbt.TaskKey[Seq[Attributed[File]]]
  ): Def.Initialize[Task[Seq[File]]] = Def.task(data(key.value).filter(_.getName.endsWith(".jar")))

  // helper methods
  private def flatLoader(classpath: Seq[File], parent: ClassLoader): ClassLoader =
    new FlatLoader(classpath.map(_.toURI.toURL).toArray, parent)
  private[this] def modifiedTimes(stamps: Seq[(Path, FileStamp)]): Seq[(File, Long)] = stamps.map {
    case (p, LastModified(lm)) => p.toFile -> lm
    case (p, _) =>
      val f = p.toFile
      f -> IO.getModifiedTimeOrZero(f)
  }
}
