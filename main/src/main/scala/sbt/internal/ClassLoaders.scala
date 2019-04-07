/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.io.File
import java.net.{ URL, URLClassLoader }

import sbt.ClassLoaderLayeringStrategy.{ ScalaInstance => ScalaInstanceLayer, _ }
import sbt.Keys._
import sbt.SlashSyntax0._
import sbt.internal.inc.ScalaInstance
import sbt.internal.inc.classpath.ClasspathUtilities
import sbt.internal.util.Attributed
import sbt.internal.util.Attributed.data
import sbt.io.IO
import sbt.librarymanagement.Configurations.{ Runtime, Test }

import scala.annotation.tailrec

private[sbt] object ClassLoaders {
  private[this] val interfaceLoader = classOf[sbt.testing.Framework].getClassLoader
  /*
   * Get the class loader for a test task. The configuration could be IntegrationTest or Test.
   */
  private[sbt] def testTask: Def.Initialize[Task[ClassLoader]] = Def.task {
    val si = scalaInstance.value
    val rawCP = data(fullClasspath.value)
    val fullCP = if (si.isManagedVersion) rawCP else si.allJars.toSeq ++ rawCP
    val exclude = dependencyJars(exportedProducts).value.toSet ++ si.allJars
    buildLayers(
      strategy = classLoaderLayeringStrategy.value,
      si = si,
      fullCP = fullCP,
      rawRuntimeDependencies =
        dependencyJars(Runtime / dependencyClasspath).value.filterNot(exclude),
      allDependencies = dependencyJars(dependencyClasspath).value.filterNot(exclude),
      runtimeCache = (Runtime / classLoaderCache).value,
      testCache = (Test / classLoaderCache).value,
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
        val runtimeCache = (Runtime / classLoaderCache).value
        val testCache = (Test / classLoaderCache).value
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
              runtimeCache = runtimeCache,
              testCache = testCache,
              resources = ClasspathUtilities.createClasspathResources(classpath, instance),
              tmp = taskTemporaryDirectory.value: @sbtUnchecked,
              scope = resolvedScope
            )
          }
        new Run(newLoader, trapExit.value)
      }
    }
  }

  /*
   * Create a layered classloader. There are up to four layers:
   * 1) the scala instance class loader
   * 2) the runtime dependencies
   * 3) the test dependencies
   * 4) the rest of the classpath
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
      runtimeCache: ClassLoaderCache,
      testCache: ClassLoaderCache,
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
          case ScalaInstanceLayer                                          => (false, false)
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

        val scalaInstanceLayer = new ScalaInstanceLoader(si)
        // layer 2
        val runtimeDependencySet = allDependenciesSet intersect allRuntimeDependencies
        val runtimeDependencies = rawRuntimeDependencies.filter(runtimeDependencySet)
        lazy val runtimeLayer =
          if (layerDependencies)
            layer(runtimeDependencies, scalaInstanceLayer, runtimeCache, resources, tmp)
          else scalaInstanceLayer

        // layer 3 (optional if testDependencies are empty)
        val testDependencySet = allTestDependencies diff runtimeDependencySet
        val testDependencies = allDependencies.filter(testDependencySet)
        val testLayer = layer(testDependencies, runtimeLayer, testCache, resources, tmp)

        // layer 4
        val dynamicClasspath =
          fullCP.filterNot(testDependencySet ++ runtimeDependencies ++ si.allJars)
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
    val (snapshots, jars) = classpath.partition(_.toString.contains("-SNAPSHOT"))
    val jarLoader = if (jars.isEmpty) parent else cache.get((jars, parent, resources, tmp))
    if (snapshots.isEmpty) jarLoader else cache.get((snapshots, jarLoader, resources, tmp))
  }

  private class ScalaInstanceLoader(val instance: ScalaInstance)
      extends URLClassLoader(instance.allJars.map(_.toURI.toURL), interfaceLoader) {
    override def equals(o: Any): Boolean = o match {
      case that: ScalaInstanceLoader => this.instance.allJars.sameElements(that.instance.allJars)
      case _                         => false
    }
    override def hashCode: Int = instance.hashCode
    override lazy val toString: String =
      s"ScalaInstanceLoader($interfaceLoader, jars = {${instance.allJars.mkString(", ")}})"
  }

  // helper methods
  private def flatLoader(classpath: Seq[File], parent: ClassLoader): ClassLoader =
    new URLClassLoader(classpath.map(_.toURI.toURL).toArray, parent) {
      override def toString: String =
        s"FlatClassLoader(parent = $interfaceLoader, jars =\n${classpath.mkString("\n")}\n)"
    }

}

private[sbt] object SbtMetaBuildClassLoader {
  private[this] implicit class Ops(val c: ClassLoader) {
    def urls: Array[URL] = c match {
      case u: URLClassLoader => u.getURLs
      case cl =>
        throw new IllegalStateException(s"sbt was launched with a non URLClassLoader: $cl")
    }
  }
  def apply(libraryLoader: ClassLoader, fullLoader: ClassLoader): ClassLoader = {
    @tailrec
    def bootLoader(classLoader: ClassLoader): ClassLoader = classLoader.getParent match {
      case null                                                               => classLoader
      case c if c.getClass.getCanonicalName == "xsbt.boot.BootFilteredLoader" => c
      case c                                                                  => bootLoader(c)
    }
    val interfaceFilter: URL => Boolean = _.getFile.endsWith("test-interface-1.0.jar")
    val (interfaceURL, rest) = fullLoader.urls.partition(interfaceFilter)
    val interfaceLoader = new URLClassLoader(interfaceURL, bootLoader(libraryLoader)) {
      override def toString: String = s"SbtTestInterfaceClassLoader(${getURLs.head})"
    }
    val updatedLibraryLoader = new URLClassLoader(libraryLoader.urls, interfaceLoader) {
      override def toString: String = s"ScalaClassLoader(jars = {${getURLs.mkString(", ")}}"
    }
    new URLClassLoader(rest, updatedLibraryLoader) {
      override def toString: String = s"SbtMetaBuildClassLoader"
    }
  }
}
