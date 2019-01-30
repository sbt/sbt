/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

import java.io.File
import java.net.URLClassLoader
import java.{ util => jutil }
import scala.collection.JavaConverters._

import sbt.internal.inc.classpath._
import sbt.io.IO

private[sbt] class LayeredClassLoader(
    classpath: Seq[File],
    parent: ClassLoader,
    override protected val resources: Map[String, String],
    tempDir: File,
) extends URLClassLoader(classpath.toArray.map(_.toURI.toURL), parent)
    with RawResources
    with NativeCopyLoader
    with AutoCloseable {
  private[this] val nativeLibs = new jutil.HashSet[File]().asScala
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
  override def close(): Unit = nativeLibs.foreach(NativeLibs.delete)
  override def toString: String = s"""LayeredClassLoader(
  |  classpath =
  |    ${classpath mkString "\n    "}
  |  parent =
  |    ${parent.toString.linesIterator.mkString("\n    ")}
  |)""".stripMargin
}

private[internal] object NativeLibs {
  private[this] val nativeLibs = new jutil.HashSet[File].asScala
  Runtime.getRuntime.addShutdownHook(new Thread("sbt.internal.native-library-deletion") {
    override def run(): Unit = {
      nativeLibs.foreach(IO.delete)
      IO.deleteIfEmpty(nativeLibs.map(_.getParentFile).toSet)
      nativeLibs.clear()
    }
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
