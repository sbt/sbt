/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

import java.io.File
import java.net.URLClassLoader
import java.util.concurrent.ConcurrentHashMap

import sbt.internal.inc.classpath._
import sbt.io.IO

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer

private[sbt] class LayeredClassLoader(
    classpath: Seq[File],
    parent: ClassLoader,
    override protected val resources: Map[String, String],
    tempDir: File,
) extends URLClassLoader(classpath.toArray.map(_.toURI.toURL), parent)
    with RawResources
    with NativeCopyLoader
    with AutoCloseable {
  private[this] val nativeLibs = new java.util.HashSet[File]().asScala
  override protected val config = new NativeCopyConfig(
    tempDir,
    classpath,
    IO.parseClasspath(System.getProperty("java.library.path", ""))
  )
  override def findLibrary(name: String): String = {
    super.findLibrary(name) match {
      case null => null
      case l =>
        nativeLibs += new File(l)
        l
    }
  }

  private[this] val loaded = new ConcurrentHashMap[String, Class[_]]
  /*
   * Override findClass to memoize its result. We need to do this because in loadClass we will
   * delegate to findClass if the current LayeredClassLoader cannot load a class but it is a
   * descendant of the thread's context class loader and a class loader below it in the layering
   * hierarchy is able to load the required class. Unlike loadClass, findClass does not cache
   * the result which would make it possible to return multiple versions of the same class.
   */
  override def findClass(name: String): Class[_] = loaded.get(name) match {
    case null =>
      val res = super.findClass(name)
      loaded.putIfAbsent(name, res) match {
        case null  => res
        case clazz => clazz
      }
    case c => c
  }
  override def loadClass(name: String, resolve: Boolean): Class[_] = {
    try super.loadClass(name, resolve)
    catch {
      case e: ClassNotFoundException =>
        val loaders = new ListBuffer[LayeredClassLoader]
        var currentLoader: ClassLoader = Thread.currentThread.getContextClassLoader
        do {
          currentLoader match {
            case cl: LayeredClassLoader if cl != this => loaders.prepend(cl)
            case _                                    =>
          }
          currentLoader = currentLoader.getParent
        } while (currentLoader != null && currentLoader != this)
        if (currentLoader == this) {
          val resourceName = name.replace('.', '/').concat(".class")
          loaders
            .collectFirst {
              case l if l.findResource(resourceName) != null =>
                val res = l.findClass(name)
                if (resolve) l.resolveClass(res)
                res
            }
            .getOrElse(throw e)
        } else throw e
    }
  }
  override def close(): Unit = nativeLibs.foreach(NativeLibs.delete)
  override def toString: String = s"""LayeredClassLoader(
  |  classpath =
  |    ${classpath mkString "\n    "}
  |  parent =
  |    ${parent.toString.linesIterator.mkString("\n    ")}
  |)""".stripMargin
}

private[internal] object NativeLibs {
  private[this] val nativeLibs = new java.util.HashSet[File].asScala
  ShutdownHooks.add(() => {
    nativeLibs.foreach(IO.delete)
    IO.deleteIfEmpty(nativeLibs.map(_.getParentFile).toSet)
    nativeLibs.clear()
  })
  def addNativeLib(lib: String): Unit = {
    nativeLibs.add(new File(lib))
    ()
  }
  def delete(file: File): Unit = {
    nativeLibs.remove(file)
    file.delete()
    ()
  }
}
