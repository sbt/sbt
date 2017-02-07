/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt
package internal
package inc

import java.io.File
import java.util.concurrent.Callable
import sbt.util.Logger
import sbt.io.IO

/**
 * A component manager provides access to the pieces of zinc that are distributed as components.
 * Compiler bridge is distributed as a source jar so that it can be compiled against a specific
 * version of Scala.
 *
 * The component manager provides services to install and retrieve components to the local filesystem.
 * This is used for compiled source jars so that the compilation need not be repeated for other projects on the same
 * machine.
 */
class ZincComponentManager(globalLock: xsbti.GlobalLock, provider: xsbti.ComponentProvider,
  secondaryCacheDir: Option[File], val log: Logger) {
  /** Get all of the files for component 'id', throwing an exception if no files exist for the component. */
  def files(id: String)(ifMissing: IfMissing): Iterable[File] =
    {
      def fromSecondary =
        lockSecondaryCache {
          update(id)
          getOrElse(createAndCache)
        } getOrElse notFound
      def getOrElse(orElse: => Iterable[File]): Iterable[File] =
        {
          val existing = provider.component(id)
          if (existing.isEmpty) orElse
          else existing
        }
      def notFound = invalid("Could not find required component '" + id + "'")
      def createAndCache =
        ifMissing match {
          case IfMissing.Fail => notFound
          case d: IfMissing.Define =>
            d.run() // this is expected to have called define.
            if (d.useSecondaryCache) {
              cacheToSecondaryCache(id)
            }
            getOrElse(notFound)
        }
      lockLocalCache { getOrElse(fromSecondary) }
    }
  /** This is used to lock the local cache in project/boot/.  By checking the local cache first, we can avoid grabbing a global lock. */
  private def lockLocalCache[T](action: => T): T = lock(provider.lockFile)(action)
  /** This is used to ensure atomic access to components in the global Ivy cache.*/
  private def lockSecondaryCache[T](action: => T): Option[T] =
    secondaryCacheDir map { dir =>
      val lockFile = new File(dir, ".sbt.cache.lock")
      lock(lockFile)(action)
    }
  private def lock[T](file: File)(action: => T): T = globalLock(file, new Callable[T] { def call = action })
  /** Get the file for component 'id', throwing an exception if no files or multiple files exist for the component. */
  def file(id: String)(ifMissing: IfMissing): File =
    files(id)(ifMissing).toList match {
      case x :: Nil => x
      case xs       => invalid("Expected single file for component '" + id + "', found: " + xs.mkString(", "))
    }
  private def invalid(msg: String) = throw new InvalidComponent(msg)

  def define(id: String, files: Iterable[File]) = lockLocalCache { provider.defineComponent(id, files.toSeq.toArray) }
  /** Retrieve the file for component 'id' from the secondary cache. */
  private def update(id: String): Unit =
    {
      secondaryCacheDir map { dir =>
        val file = seondaryCacheFile(id, dir)
        if (file.exists) {
          define(id, Seq(file))
        }
      }
      ()
    }
  /** Install the files for component 'id' to the secondary cache. */
  private def cacheToSecondaryCache(id: String): Unit =
    {
      val fromPrimaryCache = file(id)(IfMissing.fail)
      secondaryCacheDir match {
        case Some(dir) =>
          val file = seondaryCacheFile(id, dir)
          IO.copyFile(fromPrimaryCache, file)
        case _ => ()
      }
      ()
    }
  private val sbtOrg = xsbti.ArtifactInfo.SbtOrganization
  private def seondaryCacheFile(id: String, dir: File): File =
    {
      val fileName = id + "-" + ZincComponentManager.stampedVersion + ".jar"
      new File(new File(dir, sbtOrg), fileName)
    }
}
class InvalidComponent(msg: String, cause: Throwable) extends RuntimeException(msg, cause) {
  def this(msg: String) = this(msg, null)
}
sealed trait IfMissing
object IfMissing {
  def fail: IfMissing = Fail
  /** f is expected to call ZincComponentManager.define.  */
  def define(useSecondaryCache: Boolean, f: => Unit): IfMissing = new Define(useSecondaryCache, f)
  object Fail extends IfMissing
  final class Define(val useSecondaryCache: Boolean, define: => Unit) extends IfMissing {
    def run(): Unit = define
  }
}
object ZincComponentManager {
  lazy val (version, timestamp) =
    {
      val properties = new java.util.Properties
      val propertiesStream = versionResource.openStream
      try { properties.load(propertiesStream) } finally { propertiesStream.close() }
      (properties.getProperty("version"), properties.getProperty("timestamp"))
    }
  lazy val stampedVersion = version + "_" + timestamp

  import java.net.URL
  private def versionResource: URL = getClass.getResource("/incrementalcompiler.version.properties")
}
