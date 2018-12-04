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

import sbt.Keys._
import sbt.SlashSyntax0._
import sbt.internal.inc.ScalaInstance
import sbt.internal.inc.classpath.{ ClasspathUtilities, DualLoader, NullLoader }
import sbt.internal.util.Attributed
import sbt.internal.util.Attributed.data
import sbt.io.IO
import sbt.librarymanagement.Configurations.Runtime
import PrettyPrint.indent

private[sbt] object ClassLoaders {
  private[this] lazy val interfaceLoader =
    combine(
      classOf[sbt.testing.Framework].getClassLoader,
      new NullLoader,
      toString = "sbt.testing.Framework interface ClassLoader"
    )
  /*
   * Get the class loader for a test task. The configuration could be IntegrationTest or Test.
   */
  private[sbt] def testTask: Def.Initialize[Task[ClassLoader]] = Def.task {
    val si = scalaInstance.value
    val rawCP = data(fullClasspath.value)
    val fullCP = if (si.isManagedVersion) rawCP else si.allJars.toSeq ++ rawCP
    val strategy = layeringStrategy.value
    val runtimeCache = (Runtime / classLoaderCache).value
    val testCache = classLoaderCache.value
    val tmp = IO.createUniqueDirectory(taskTemporaryDirectory.value)
    val resources = ClasspathUtilities.createClasspathResources(fullCP, si)

    val raw = strategy match {
      case LayeringStrategy.Flat => flatLoader(rawCP, interfaceLoader)
      case s                     =>
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
        val (layerDependencies, layerTestDependencies) = s match {
          case LayeringStrategy.Full                => (true, true)
          case LayeringStrategy.ScalaInstance       => (false, false)
          case LayeringStrategy.RuntimeDependencies => (true, false)
          case _                                    => (false, true)
        }
        // Do not include exportedProducts in any cached layers because they may change between runs.
        val exclude = dependencyJars(exportedProducts).value.toSet ++ si.allJars.toSeq
        // The raw declarations are to avoid having to make a dynamic task. The
        // allDependencies and allTestDependencies create a mutually exclusive list of jar
        // dependencies for layers 2 and 3
        val rawTestDependencies = dependencyJars(dependencyClasspath).value.filterNot(exclude)
        val allTestDependencies = (if (layerTestDependencies) rawTestDependencies else Nil).toSet
        val rawDependencies =
          dependencyJars(Runtime / dependencyClasspath).value.filterNot(exclude)
        val allDependencies = (if (layerDependencies) rawDependencies else Nil).toSet

        // layer 2
        val runtimeDependencies = allTestDependencies intersect allDependencies
        val runtimeLayer =
          layer(runtimeDependencies.toSeq, loader(si), runtimeCache, resources, tmp)

        // layers 3 (optional if testDependencies are empty)

        // The top layer needs to include the interface jar or else the test task cannot be created.
        // It needs to be separated from the runtimeLayer or else the runtimeLayer cannot be
        // shared between the runtime and test tasks.
        val top = combine(interfaceLoader, runtimeLayer)
        val testDependencies = allTestDependencies diff runtimeDependencies
        val testLayer = layer(testDependencies.toSeq, top, testCache, resources, tmp)

        // layer 4
        val dynamicClasspath =
          fullCP.filterNot(testDependencies ++ runtimeDependencies ++ si.allJars)
        if (dynamicClasspath.nonEmpty)
          new LayeredClassLoader(dynamicClasspath, testLayer, resources, tmp)
        else testLayer
    }
    ClasspathUtilities.filterByClasspath(fullCP, raw)
  }

  private[sbt] def runner: Def.Initialize[Task[ScalaRun]] = Def.taskDyn {
    val tmp = taskTemporaryDirectory.value
    val resolvedScope = resolvedScoped.value.scope
    val instance = scalaInstance.value
    val s = streams.value
    val opts = forkOptions.value
    val options = javaOptions.value
    val exclude = dependencyJars(Runtime / exportedProducts).value.toSet ++ instance.allJars
    val dependencies = dependencyJars(Runtime / dependencyClasspath).value.filterNot(exclude)
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
        val cache = (Runtime / classLoaderCache).value
        val newLoader =
          (classpath: Seq[File]) => {
            val resources = ClasspathUtilities.createClasspathResources(classpath, instance)
            val classLoader = layeringStrategy.value match {
              case LayeringStrategy.Flat =>
                ClasspathUtilities
                  .toLoader(Nil, flatLoader(classpath, new NullLoader), resources, tmp)
              case _ =>
                val dependencyLoader = layer(dependencies, loader(instance), cache, resources, tmp)
                val dynamicClasspath = (classpath.toSet -- dependencies).toSeq
                new LayeredClassLoader(dynamicClasspath, dependencyLoader, resources, tmp)
            }
            ClasspathUtilities.filterByClasspath(classpath, classLoader)
          }
        new Run(newLoader, trapExit.value)
      }
    }
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

  // Code related to combining two classloaders that primarily exists so the test loader correctly
  // loads the testing framework using the same classloader as sbt itself.
  private val interfaceFilter = (name: String) =>
    name.startsWith("org.scalatools.testing.") || name.startsWith("sbt.testing.") || name
      .startsWith("java.") || name.startsWith("sun.")
  private val notInterfaceFilter = (name: String) => !interfaceFilter(name)
  private class WrappedDualLoader(
      val parent: ClassLoader,
      val child: ClassLoader,
      string: => String
  ) extends ClassLoader(
        new DualLoader(parent, interfaceFilter, _ => false, child, notInterfaceFilter, _ => true)
      ) {
    override def equals(o: Any): Boolean = o match {
      case that: WrappedDualLoader => this.parent == that.parent && this.child == that.child
      case _                       => false
    }
    override def hashCode: Int = (parent.hashCode * 31) ^ child.hashCode
    override lazy val toString: String = string
  }
  private def combine(parent: ClassLoader, child: ClassLoader, toString: String): ClassLoader =
    new WrappedDualLoader(parent, child, toString)
  private def combine(parent: ClassLoader, child: ClassLoader): ClassLoader =
    new WrappedDualLoader(
      parent,
      child,
      s"WrappedDualLoader(\n  parent =\n${indent(parent, 4)}"
        + s"\n  child =\n${indent(child, 4)}\n)"
    )

  // helper methods
  private def flatLoader(classpath: Seq[File], parent: ClassLoader): ClassLoader =
    new URLClassLoader(classpath.map(_.toURI.toURL).toArray, parent)

  // This makes the toString method of the ScalaInstance classloader much more readable, but
  // it is not strictly necessary.
  private def loader(si: ScalaInstance): ClassLoader = new ClassLoader(si.loader) {
    override lazy val toString: String =
      "ScalaInstanceClassLoader(\n  instance = " +
        s"${indent(si.toString.split(",").mkString("\n  ", ",\n  ", "\n"), 4)}\n)"
    // Delegate equals to that.equals in case that is itself some kind of wrapped classloader that
    // needs to delegate its equals method to the delegated ClassLoader.
    override def equals(that: Any): Boolean = if (that != null) that.equals(si.loader) else false
    override def hashCode: Int = si.loader.hashCode
  }
}
