/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010 Mark Harrah
 */
package sbt.internal.librarymanagement

import java.io.File
import java.util.concurrent.Callable
import sbt.util.Logger
import sbt.librarymanagement.*

/**
 * A component manager provides access to the pieces of xsbt that are distributed as components.
 * There are two types of components.  The first type is compiled subproject jars with their dependencies.
 * The second type is a subproject distributed as a source jar so that it can be compiled against a specific
 * version of Scala.
 *
 * The component manager provides services to install and retrieve components to the local repository.
 * This is used for compiled source jars so that the compilation need not be repeated for other projects on the same
 * machine.
 */
class ComponentManager(
    globalLock: xsbti.GlobalLock,
    provider: xsbti.ComponentProvider,
    ivyHome: Option[File],
    val log: Logger
) {
  private val ivyCache = new IvyCache(ivyHome)

  /** Get all of the files for component 'id', throwing an exception if no files exist for the component. */
  def files(id: String)(ifMissing: IfMissing): Iterable[File] = {
    def fromGlobal =
      lockGlobalCache {
        try {
          update(id); getOrElse(createAndCache)
        } catch {
          case _: NotInCache => createAndCache
        }
      }
    def getOrElse(orElse: => Iterable[File]): Iterable[File] = {
      val existing = provider.component(id)
      if (existing.isEmpty) orElse else existing
    }
    def notFound = invalid("Could not find required component '" + id + "'")
    def createAndCache =
      ifMissing match {
        case IfMissing.Fail => notFound
        case d: IfMissing.Define =>
          d()
          if (d.cache) cache(id)
          getOrElse(notFound)
      }

    lockLocalCache { getOrElse(fromGlobal) }
  }

  /** This is used to lock the local cache in project/boot/.  By checking the local cache first, we can avoid grabbing a global lock. */
  private def lockLocalCache[T](action: => T): T = lock(provider.lockFile)(action)

  /** This is used to ensure atomic access to components in the global Ivy cache. */
  private def lockGlobalCache[T](action: => T): T = lock(ivyCache.lockFile)(action)
  private def lock[T](file: File)(action: => T): T =
    globalLock(file, new Callable[T] { def call = action })

  /** Get the file for component 'id', throwing an exception if no files or multiple files exist for the component. */
  def file(id: String)(ifMissing: IfMissing): File =
    files(id)(ifMissing).toList match {
      case x :: Nil => x
      case xs =>
        invalid("Expected single file for component '" + id + "', found: " + xs.mkString(", "))
    }
  private def invalid(msg: String) = throw new InvalidComponent(msg)

  def define(id: String, files: Iterable[File]) = lockLocalCache {
    provider.defineComponent(id, files.toSeq.toArray)
  }

  /** Retrieve the file for component 'id' from the local repository. */
  private def update(id: String): Unit =
    ivyCache.withCachedJar(sbtModuleID(id), Some(globalLock), log)(jar => define(id, Seq(jar)))

  private def sbtModuleID(id: String) =
    ModuleID(SbtArtifacts.Organization, id, ComponentManager.stampedVersion)

  /** Install the files for component 'id' to the local repository.  This is usually used after writing files to the directory returned by 'location'. */
  def cache(id: String): Unit =
    ivyCache.cacheJar(sbtModuleID(id), file(id)(IfMissing.Fail), Some(globalLock), log)
  def clearCache(id: String): Unit = lockGlobalCache {
    ivyCache.clearCachedJar(sbtModuleID(id), Some(globalLock), log)
  }
}
class InvalidComponent(msg: String, cause: Throwable) extends RuntimeException(msg, cause) {
  def this(msg: String) = this(msg, null)
}
sealed trait IfMissing
object IfMissing {
  object Fail extends IfMissing
  final class Define(val cache: Boolean, define: => Unit) extends IfMissing {
    def apply() = define
  }
}
object ComponentManager {
  lazy val (version, timestamp) = {
    val properties = new java.util.Properties
    val propertiesStream = getClass.getResourceAsStream("/xsbt.version.properties")
    try {
      properties.load(propertiesStream)
    } finally {
      propertiesStream.close()
    }
    (properties.getProperty("version"), properties.getProperty("timestamp"))
  }
  lazy val stampedVersion = version + "_" + timestamp
}
