/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

/**
 * Represents a ClassLoader layering strategy. By providing an instance of [[ClassLoaderLayeringStrategy]],
 * users can configure the strategy that they want to use in various sbt tasks, most importantly
 * [[Keys.run]] and [[Keys.test]]. This setting is only relevant if fork := false in the task for
 * which we obtain a ClassLoaderLayeringStrategy.
 *
 * ClassLoaders can be composed of multiple ClassLoaders
 * to form a graph for loading a class. The different portions of the graph may be cached and
 * reused to minimize both the memory taken up by ClassLoaders (and the classes that they load) and
 * the startup time for tasks like test and run. For example, the scala library is large and takes
 * a while just to load the classes in predef. The [[Keys.scalaInstance]] task provides access to
 * a classloader that can load all of the java bootstrap classes and scala.*. Supposing that we want
 * to run code in a jar containing scala code called "foo_2.12.jar" in the base directory and that
 * we have a scala instance in scope and suppose further that "foo_2.12.jar" contains a main method
 * in the class foo.Main, then we can invoke foo.Main.main like so
 *
 * {{{
 *   val fooJarFile = new File("foo_2.12.jar")
 *   val classLoader = new URLClassLoader(
 *     Array(fooJarFile.toURI.toURL), scalaInstance.loaderLibraryOnly)
 *   val main = classLoader.loadClass("foo.Main").getDeclaredMethod("main", classOf[Array[String]])
 *   main.invoke(null, Array.empty[String])
 * }}}
 *
 * Now suppose that we have an alternative jar "foo_alt_2.12.jar" that also provides foo.Main, then
 * we can run that main method:
 *
 * {{{
 *   val fooJarFile = new File("foo_alt_2.12.jar")
 *   val altClassLoader = new URLClassLoader(
 *     Array(fooAltJarFile.toURI.toURL), scalaInstance.loaderLibraryOnly)
 *   val altMain = classLoader.loadClass("foo.Main").getDeclaredMethod("main", classOf[Array[String]])
 *   altMain.invoke(null, Array.empty[String])
 * }}}
 *
 * In the second invocation, the scala library will have already been loaded by the
 * scalaInstance.loaderLibraryOnly ClassLoader. This can reduce the startup time by O(500ms) and
 * prevents an accumulation of scala related Class objects. Note that these ClassLoaders should
 * only be used at a code boundary such that their loaded classes do not leak outside of the
 * defining scope. This is because the layered class loaders can create mutually incompatible
 * classes. For example, in the example above, suppose that there is a class foo.Bar provided
 * by both "foo_2.12.jar" and "foo_2.12.jar" and that both also provide a static method
 * "foo.Foo$.bar" that returns an instance of foo.Bar, then the following code will not work:
 *
 * {{{
 *  Thread.currentThread.setContextClassLoader(altClassLoader)
 *  val bar: Object = classLoader.loadClass("foo.Foo$").getDeclaredMethod("bar").invoke(null)
 *  val barTyped: foo.Bar = bar.asInstanceOf[foo.Bar]
 *  // throws ClassCastException because the thread context class loader is altClassLoader, but
 *  // but bar was loaded by classLoader.
 * }}}
 *
 * In general, this should only happen if the user explicitly overrides the thread context
 * ClassLoader or uses reflection to manipulate classes loaded by different loaders.
 */
sealed trait ClassLoaderLayeringStrategy

/**
 * Provides instances of [[ClassLoaderLayeringStrategy]] that can be used to define the ClassLoader used by
 * [[Keys.run]], [[Keys.test]] or any other task that runs java code inside of the sbt jvm.
 */
object ClassLoaderLayeringStrategy {

  /**
   * Include all of the dependencies in the loader. The base loader will be the Application
   * ClassLoader. All classes apart from system classes will be reloaded with each run.
   */
  case object Flat extends ClassLoaderLayeringStrategy

  /**
   * Add a layer for the scala library class loader.
   */
  sealed trait ScalaLibrary extends ClassLoaderLayeringStrategy

  /**
   * This should indicate that we use a two layer ClassLoader where the top layer is the scala
   * instance and all of the dependencies and project class paths are included in the search path
   * of the second layer.
   */
  case object ScalaLibrary extends ScalaLibrary

  /**
   * Add a layer on top of the ScalaLibrary layer for all of the task jar dependencies.
   */
  sealed trait AllLibraryJars extends ScalaLibrary

  /**
   * Add a layer on top of the ScalaLibrary layer for all of the jar dependencies.
   */
  object AllLibraryJars extends AllLibraryJars
}
