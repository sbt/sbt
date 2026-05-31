/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.util

import com.github.benmanes.caffeine.cache.{ Cache as CCache, Caffeine, Weigher }
import java.nio.file.{ Files, NoSuchFileException }
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.atomic.{ AtomicLong, AtomicReference }
import sjsonnew.BasicJsonProtocol
import xsbti.{ FileConverter, HashedVirtualFileRef, PathBasedFile, VirtualFileRef }

object CacheImplicits extends CacheImplicits:
  private[sbt] val defaultLocalDigestCacheByteSize = 1024L * 1024L
end CacheImplicits

trait CacheImplicits extends BasicCacheImplicits with BasicJsonProtocol:
  private val localDigestCacheByteSize = AtomicLong(CacheImplicits.defaultLocalDigestCacheByteSize)
  private val weigher: Weigher[String, (String, Long, Long)] = { case (k, (v1, _, _)) =>
    k.size + v1.size + 16
  }
  private val digestWeigher: Weigher[String, (Digest, Long, Long)] = { case (k, (v1, _, _)) =>
    k.size + v1.digestSize + 16
  }

  private val stampCache: AtomicReference[CCache[String, (String, Long, Long)]] =
    AtomicReference(
      Caffeine
        .newBuilder()
        .maximumWeight(localDigestCacheByteSize.get())
        .weigher(weigher)
        .build()
    )

  private val digestCache: AtomicReference[CCache[String, (Digest, Long, Long)]] =
    AtomicReference(
      Caffeine
        .newBuilder()
        .maximumWeight(localDigestCacheByteSize.get())
        .weigher(digestWeigher)
        .build()
    )

  private[sbt] def setCacheSize(size: Long): Unit =
    if localDigestCacheByteSize.get() == size then ()
    else
      localDigestCacheByteSize.set(size)
      stampCache.get().invalidateAll()
      stampCache.set(
        Caffeine
          .newBuilder()
          .maximumWeight(localDigestCacheByteSize.get())
          .weigher(weigher)
          .build()
      )
      digestCache.get().invalidateAll()
      digestCache.set(
        Caffeine
          .newBuilder()
          .maximumWeight(localDigestCacheByteSize.get())
          .weigher(digestWeigher)
          .build()
      )

  private def getOrElseUpdate(ref: HashedVirtualFileRef, lastModified: Long, sizeBytes: Long)(
      value: => String
  ) =
    Option(stampCache.get().getIfPresent(ref.id())) match
      case Some((v, mod, i)) if lastModified == mod && sizeBytes == i => v
      case _                                                          =>
        val v = value
        stampCache.get().put(ref.id(), (v, lastModified, sizeBytes))
        v

  private def getOrElseUpdate(ref: VirtualFileRef, lastModified: Long, sizeBytes: Long)(
      value: => Digest
  ) =
    Option(digestCache.get().getIfPresent(ref.id())) match
      case Some((v, mod, i)) if lastModified == mod && sizeBytes == i => v
      case _                                                          =>
        val v = value
        digestCache.get().put(ref.id(), (v, lastModified, sizeBytes))
        v

  /**
   * A string representation of HashedVirtualFileRef, delimited by `>`.
   */
  override def hashedVirtualFileRefToStr(ref: HashedVirtualFileRef): String =
    def fallback: String = super.hashedVirtualFileRefToStr(ref)
    if ref.id().endsWith(".scala") || ref.id().endsWith(".java") then fallback
    else
      ref match
        case pbf: PathBasedFile =>
          val path = pbf.toPath
          try
            val attrs = Files.readAttributes(path, classOf[BasicFileAttributes])
            if attrs.isDirectory then fallback
            else
              val lastModified = attrs.lastModifiedTime().toMillis()
              val sizeBytes = attrs.size()
              getOrElseUpdate(ref, lastModified, sizeBytes)(fallback)
          catch case e: NoSuchFileException => throw e
        case _ => fallback

  def virtualFileRefToDigest(vf: VirtualFileRef)(converter: FileConverter): Digest =
    vf match
      case pbf: PathBasedFile =>
        val path = pbf.toPath
        val attrs = Files.readAttributes(path, classOf[BasicFileAttributes])
        def fallback: Digest = Digest.sha256Hash(path)
        if attrs.isDirectory then sys.error(s"$vf is a directory")
        else
          val lastModified = attrs.lastModifiedTime().toMillis()
          val sizeBytes = attrs.size()
          vf match
            case h: HashedVirtualFileRef =>
              getOrElseUpdate(vf, lastModified, sizeBytes)(Digest(h))
            case _ =>
              getOrElseUpdate(vf, lastModified, sizeBytes)(fallback)
      case _ => Digest.sha256Hash(converter.toPath(vf))
end CacheImplicits
