/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.io.File
import java.net.URLClassLoader
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
    val rawCP = modifiedTimes((outputFileStamps in classpathFiles).value)
    val fullCP =
      if (si.isManagedVersion) rawCP
      else si.libraryJars.map(j => j -> IO.getModifiedTimeOrZero(j)).toSeq ++ rawCP
    val exclude = dependencyJars(exportedProducts).value.toSet ++ si.libraryJars
    val resourceCP = modifiedTimes((outputFileStamps in resources).value)
    buildLayers(
      strategy = classLoaderLayeringStrategy.value,
      si = si,
      fullCP = fullCP,
      resourceCP = resourceCP,
      allDependencies = dependencyJars(dependencyClasspath).value.filterNot(exclude),
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
    val resourceCP = modifiedTimes((outputFileStamps in resources).value)
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
            buildLayers(
              strategy = classLoaderLayeringStrategy.value: @sbtUnchecked,
              si = instance,
              fullCP = classpath.map(f => f -> IO.getModifiedTimeOrZero(f)),
              resourceCP = resourceCP,
              allDependencies = allDeps,
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
      resourceCP: Seq[(File, Long)],
      allDependencies: Seq[File],
      cache: ClassLoaderCache,
      resources: Map[String, String],
      tmp: File,
      scope: Scope
  ): ClassLoader = {
    val cpFiles = fullCP.map(_._1)
    val raw = strategy match {
      case Flat => flatLoader(cpFiles, interfaceLoader)
      case _ =>
        val layerDependencies = strategy match {
          case _: AllLibraryJars => true
          case _                 => false
        }
        val scalaLibraryLayer = layer(si.libraryJars, interfaceLoader, cache, resources, tmp)
        val cpFiles = fullCP.map(_._1)

        val scalaReflectJar = allDependencies.collectFirst {
          case f if f.getName == "scala-reflect.jar" =>
            si.allJars.find(_.getName == "scala-reflect.jar")
        }.flatten
        class ScalaReflectClassLoader(jar: File)
            extends URLClassLoader(Array(jar.toURI.toURL), scalaLibraryLayer) {
          override def toString: String =
            s"ScalaReflectClassLoader($jar, parent = $scalaLibraryLayer)"
        }
        val scalaReflectLayer = scalaReflectJar
          .map { file =>
            cache.apply(
              file -> IO.getModifiedTimeOrZero(file) :: Nil,
              scalaLibraryLayer,
              () => new ScalaReflectClassLoader(file)
            )
          }
          .getOrElse(scalaLibraryLayer)

        // layer 2 (resources)
        val resourceLayer =
          if (layerDependencies)
            getResourceLayer(cpFiles, resourceCP, scalaReflectLayer, cache, resources)
          else scalaReflectLayer

        // layer 3 (optional if in the test config and the runtime layer is not shared)
        val dependencyLayer =
          if (layerDependencies) layer(allDependencies, resourceLayer, cache, resources, tmp)
          else resourceLayer

        val scalaJarNames = (si.libraryJars ++ scalaReflectJar).map(_.getName).toSet
        // layer 4
        val filteredSet =
          if (layerDependencies) allDependencies.toSet ++ si.libraryJars ++ scalaReflectJar
          else Set(si.libraryJars ++ scalaReflectJar: _*)
        val dynamicClasspath = cpFiles.filterNot(f => filteredSet(f) || scalaJarNames(f.getName))
        new LayeredClassLoader(dynamicClasspath, dependencyLayer, resources, tmp)
    }
    ClasspathUtilities.filterByClasspath(cpFiles, raw)
  }

  private def dependencyJars(
      key: sbt.TaskKey[Seq[Attributed[File]]]
  ): Def.Initialize[Task[Seq[File]]] = Def.task(data(key.value).filter(_.getName.endsWith(".jar")))

  // Creates a one or two layered classloader for the provided classpaths depending on whether
  // or not the classpath contains any snapshots. If it does, the snapshots are placed in a layer
  // above the regular jar layer. This allows the snapshot layer to be invalidated without
  // invalidating the regular jar layer. If the classpath is empty, it just returns the parent
  // loader.
  private def layer(
      classpath: Seq[File],
      parent: ClassLoader,
      cache: ClassLoaderCache,
      resources: Map[String, String],
      tmp: File
  ): ClassLoader = {
    if (classpath.nonEmpty) {
      cache(
        classpath.toList.map(f => f -> IO.getModifiedTimeOrZero(f)),
        parent,
        () => new LayeredClassLoader(classpath, parent, resources, tmp)
      )
    } else parent
  }

  private class ResourceLoader(
      classpath: Seq[File],
      parent: ClassLoader,
      resources: Map[String, String]
  ) extends LayeredClassLoader(classpath, parent, resources, new File("/dev/null")) {
    override def findClass(name: String): Class[_] = throw new ClassNotFoundException(name)
    override def loadClass(name: String, resolve: Boolean): Class[_] = {
      val clazz = parent.loadClass(name)
      if (resolve) resolveClass(clazz)
      clazz
    }
    override def toString: String = "ResourceLoader"
  }
  // Creates a one or two layered classloader for the provided classpaths depending on whether
  // or not the classpath contains any snapshots. If it does, the snapshots are placed in a layer
  // above the regular jar layer. This allows the snapshot layer to be invalidated without
  // invalidating the regular jar layer. If the classpath is empty, it just returns the parent
  // loader.
  private def getResourceLayer(
      classpath: Seq[File],
      resources: Seq[(File, Long)],
      parent: ClassLoader,
      cache: ClassLoaderCache,
      resourceMap: Map[String, String]
  ): ClassLoader = {
    if (resources.nonEmpty) {
      cache(
        resources.toList,
        parent,
        () => new ResourceLoader(classpath, parent, resourceMap)
      )
    } else parent
  }

  private[this] class FlatLoader(classpath: Seq[File], parent: ClassLoader)
      extends URLClassLoader(classpath.map(_.toURI.toURL).toArray, parent) {
    override def toString: String =
      s"FlatClassLoader(parent = $interfaceLoader, jars =\n${classpath.mkString("\n")}\n)"
  }
  // helper methods
  private def flatLoader(classpath: Seq[File], parent: ClassLoader): ClassLoader =
    new FlatLoader(classpath, parent)
  private[this] def modifiedTimes(stamps: Seq[(Path, FileStamp)]): Seq[(File, Long)] = stamps.map {
    case (p, LastModified(lm)) => p.toFile -> lm
    case (p, _) =>
      val f = p.toFile
      f -> IO.getModifiedTimeOrZero(f)
  }
}
