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

import sbt.ClassLoaderLayeringStrategy._
import sbt.Keys._
import sbt.SlashSyntax0._
import sbt.internal.classpath.ClassLoaderCache
import sbt.internal.inc.ScalaInstance
import sbt.internal.inc.classpath.ClasspathUtilities
import sbt.internal.util.Attributed
import sbt.internal.util.Attributed.data
import sbt.io.IO
import sbt.librarymanagement.Configurations.Runtime

private[sbt] object ClassLoaders {
  private[this] val interfaceLoader = classOf[sbt.testing.Framework].getClassLoader
  /*
   * Get the class loader for a test task. The configuration could be IntegrationTest or Test.
   */
  private[sbt] def testTask: Def.Initialize[Task[ClassLoader]] = Def.task {
    val si = scalaInstance.value
    val rawCP = data(fullClasspath.value)
    val fullCP = if (si.isManagedVersion) rawCP else List(si.libraryJar) ++ rawCP
    val exclude = dependencyJars(exportedProducts).value.toSet ++ Set(si.libraryJar)
    buildLayers(
      strategy = classLoaderLayeringStrategy.value,
      si = si,
      fullCP = fullCP,
      rawRuntimeDependencies =
        dependencyJars(Runtime / dependencyClasspath).value.filterNot(exclude),
      allDependencies = dependencyJars(dependencyClasspath).value.filterNot(exclude),
      cache = extendedClassLoaderCache.value,
      resources = ClasspathUtilities.createClasspathResources(fullCP, si),
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
        val exclude = dependencyJars(exportedProducts).value.toSet ++ instance.allJars
        val runtimeDeps = dependencyJars(Runtime / dependencyClasspath).value.filterNot(exclude)
        val allDeps = dependencyJars(dependencyClasspath).value.filterNot(exclude)
        val newLoader =
          (classpath: Seq[File]) => {
            buildLayers(
              strategy = classLoaderLayeringStrategy.value: @sbtUnchecked,
              si = instance,
              fullCP = classpath,
              rawRuntimeDependencies = runtimeDeps,
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
   * Create a layered classloader. There are up to five layers:
   * 1) the scala instance class loader
   * 2) the resource layer
   * 3) the runtime dependencies
   * 4) the test dependencies
   * 5) the rest of the classpath
   * The first two layers may be optionally cached to reduce memory usage and improve
   * start up latency. Because there may be mutually incompatible libraries in the runtime
   * and test dependencies, it's important to be able to configure which layers are used.
   */
  private def buildLayers(
      strategy: ClassLoaderLayeringStrategy,
      si: ScalaInstance,
      fullCP: Seq[File],
      rawRuntimeDependencies: Seq[File],
      allDependencies: Seq[File],
      cache: ClassLoaderCache,
      resources: Map[String, String],
      tmp: File,
      scope: Scope
  ): ClassLoader = {
    val isTest = scope.config.toOption.map(_.name) == Option("test")
    val raw = strategy match {
      case Flat => flatLoader(fullCP, interfaceLoader)
      case _ =>
        val (layerDependencies, layerTestDependencies) = strategy match {
          case ShareRuntimeDependenciesLayerWithTestDependencies if isTest => (true, true)
          case ScalaLibrary                                                => (false, false)
          case RuntimeDependencies                                         => (true, false)
          case TestDependencies if isTest                                  => (false, true)
          case badStrategy =>
            val msg = s"Layering strategy $badStrategy is not valid for the classloader in " +
              s"$scope. Valid options are: ClassLoaderLayeringStrategy.{ " +
              "Flat, ScalaInstance, RuntimeDependencies }"
            throw new IllegalArgumentException(msg)
        }
        val allDependenciesSet = allDependencies.toSet
        // The raw declarations are to avoid having to make a dynamic task. The
        // allDependencies and allTestDependencies create a mutually exclusive list of jar
        // dependencies for layers 2 and 3. Note that in the Runtime or Compile configs, it
        // should always be the case that allTestDependencies == Nil
        val allTestDependencies = if (layerTestDependencies) allDependenciesSet else Set.empty[File]
        val allRuntimeDependencies = (if (layerDependencies) rawRuntimeDependencies else Nil).toSet

        val scalaLibraryLayer = layer(si.libraryJar :: Nil, interfaceLoader, cache, resources, tmp)

        // layer 2 (resources)
        val resourceLayer =
          if (layerDependencies) getResourceLayer(fullCP, scalaLibraryLayer, cache, resources)
          else scalaLibraryLayer

        // layer 3 (optional if in the test config and the runtime layer is not shared)
        val runtimeDependencySet = allDependenciesSet intersect allRuntimeDependencies
        val runtimeDependencies = rawRuntimeDependencies.filter(runtimeDependencySet)
        lazy val runtimeLayer =
          if (layerDependencies)
            layer(runtimeDependencies, resourceLayer, cache, resources, tmp)
          else resourceLayer

        // layer 4 (optional if testDependencies are empty)
        val testDependencySet = allTestDependencies diff runtimeDependencySet
        val testDependencies = allDependencies.filter(testDependencySet)
        val testLayer = layer(testDependencies, runtimeLayer, cache, resources, tmp)

        // layer 5
        val dynamicClasspath =
          fullCP.filterNot(testDependencySet ++ runtimeDependencies + si.libraryJar)
        if (dynamicClasspath.nonEmpty)
          new LayeredClassLoader(dynamicClasspath, testLayer, resources, tmp)
        else testLayer
    }
    ClasspathUtilities.filterByClasspath(fullCP, raw)
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
      parent: ClassLoader,
      cache: ClassLoaderCache,
      resources: Map[String, String]
  ): ClassLoader = {
    if (classpath.nonEmpty) {
      cache(
        classpath.toList.map(f => f -> IO.getModifiedTimeOrZero(f)),
        parent,
        () => new ResourceLoader(classpath, parent, resources)
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
}
