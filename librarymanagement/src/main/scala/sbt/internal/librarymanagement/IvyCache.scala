/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah
 */
package sbt.internal.librarymanagement

import java.io.File

import org.apache.ivy.core.cache.{
  ArtifactOrigin,
  CacheDownloadOptions,
  DefaultRepositoryCacheManager
}
import org.apache.ivy.core.module.descriptor.{ Artifact => IvyArtifact, DefaultArtifact }
import org.apache.ivy.plugins.repository.file.{ FileRepository => IvyFileRepository, FileResource }
import org.apache.ivy.plugins.repository.{ ArtifactResourceResolver, Resource, ResourceDownloader }
import org.apache.ivy.plugins.resolver.util.ResolvedResource
import org.apache.ivy.util.FileUtil
import sbt.io.Path
import sbt.librarymanagement._
import sbt.util.Logger

import scalajson.ast.unsafe._
import scala.collection.mutable
import jawn.{ SupportParser, MutableFacade }

class NotInCache(val id: ModuleID, cause: Throwable)
    extends RuntimeException(NotInCache(id, cause), cause) {
  def this(id: ModuleID) = this(id, null)
}
private object NotInCache {
  def apply(id: ModuleID, cause: Throwable) = {
    val postfix = if (cause == null) "" else (": " + cause.toString)
    "File for " + id + " not in cache" + postfix
  }
}

/** Provides methods for working at the level of a single jar file with the default Ivy cache.*/
class IvyCache(val ivyHome: Option[File]) {
  def lockFile = new File(ivyHome getOrElse Path.userHome, ".sbt.cache.lock")

  /** Caches the given 'file' with the given ID.  It may be retrieved or cleared using this ID.*/
  def cacheJar(moduleID: ModuleID, file: File, lock: Option[xsbti.GlobalLock], log: Logger): Unit = {
    val artifact = defaultArtifact(moduleID)
    val resolved =
      new ResolvedResource(new FileResource(new IvyFileRepository, file), moduleID.revision)
    withDefaultCache(lock, log) { cache =>
      val resolver = new ArtifactResourceResolver { def resolve(artifact: IvyArtifact) = resolved }
      cache.download(artifact, resolver, new FileDownloader, new CacheDownloadOptions)
      ()
    }
  }

  /** Clears the cache of the jar for the given ID.*/
  def clearCachedJar(id: ModuleID, lock: Option[xsbti.GlobalLock], log: Logger): Unit = {
    try { withCachedJar(id, lock, log)(_.delete); () } catch {
      case e: Exception => log.debug("Error cleaning cached jar: " + e.toString)
    }
  }

  /** Copies the cached jar for the given ID to the directory 'toDirectory'.  If the jar is not in the cache, NotInCache is thrown.*/
  def retrieveCachedJar(
      id: ModuleID,
      toDirectory: File,
      lock: Option[xsbti.GlobalLock],
      log: Logger
  ) =
    withCachedJar(id, lock, log) { cachedFile =>
      val copyTo = new File(toDirectory, cachedFile.getName)
      FileUtil.copy(cachedFile, copyTo, null)
      copyTo
    }

  /** Get the location of the cached jar for the given ID in the Ivy cache.  If the jar is not in the cache, NotInCache is thrown .*/
  def withCachedJar[T](id: ModuleID, lock: Option[xsbti.GlobalLock], log: Logger)(
      f: File => T
  ): T = {
    val cachedFile =
      try {
        withDefaultCache(lock, log) { cache =>
          val artifact = defaultArtifact(id)
          cache.getArchiveFileInCache(artifact, unknownOrigin(artifact))
        }
      } catch { case e: Exception => throw new NotInCache(id, e) }

    if (cachedFile.exists) f(cachedFile) else throw new NotInCache(id)
  }

  /** Calls the given function with the default Ivy cache.*/
  def withDefaultCache[T](lock: Option[xsbti.GlobalLock], log: Logger)(
      f: DefaultRepositoryCacheManager => T
  ): T = {
    val (ivy, _) = basicLocalIvy(lock, log)
    ivy.withIvy(log) { ivy =>
      val cache = ivy.getSettings.getDefaultRepositoryCacheManager
        .asInstanceOf[DefaultRepositoryCacheManager]
      cache.setUseOrigin(false)
      f(cache)
    }
  }
  private def unknownOrigin(artifact: IvyArtifact) = ArtifactOrigin.unkwnown(artifact)

  /** A minimal Ivy setup with only a local resolver and the current directory as the base directory.*/
  private def basicLocalIvy(lock: Option[xsbti.GlobalLock], log: Logger) = {
    val local = Resolver.defaultLocal
    val paths = IvyPaths(new File("."), ivyHome)
    val conf = new InlineIvyConfiguration(
      paths,
      Vector(local),
      Vector.empty,
      Vector.empty,
      lock,
      IvySbt.DefaultChecksums,
      false,
      None,
      UpdateOptions(),
      log
    )
    (new IvySbt(conf), local)
  }

  /** Creates a default jar artifact based on the given ID.*/
  private def defaultArtifact(moduleID: ModuleID): IvyArtifact =
    new DefaultArtifact(IvySbt.toID(moduleID), null, moduleID.name, "jar", "jar")
}

/** Required by Ivy for copying to the cache.*/
private class FileDownloader extends ResourceDownloader {
  def download(artifact: IvyArtifact, resource: Resource, dest: File): Unit = {
    if (dest.exists()) dest.delete()
    val part = new File(dest.getAbsolutePath + ".part")
    FileUtil.copy(resource.openStream, part, null)
    if (!part.renameTo(dest))
      sys.error("Could not move temporary file " + part + " to final location " + dest)
  }
}

object FixedParser extends SupportParser[JValue] {
  implicit val facade: MutableFacade[JValue] =
    new MutableFacade[JValue] {
      def jnull() = JNull
      def jfalse() = JFalse
      def jtrue() = JTrue
      def jnum(s: String) = JNumber(s)
      def jint(s: String) = JNumber(s)
      def jstring(s: String) = JString(s)
      def jarray(vs: mutable.ArrayBuffer[JValue]) = JArray(vs.toArray)
      def jobject(vs: mutable.Map[String, JValue]) = {
        val array = new Array[JField](vs.size)
        var i = 0
        vs.foreach {
          case (key, value) =>
            array(i) = JField(key, value)
            i += 1
        }
        JObject(array)
      }
    }
}
