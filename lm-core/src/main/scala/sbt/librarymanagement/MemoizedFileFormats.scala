/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.librarymanagement

import java.io.File
import java.net.URI
import java.nio.file.{ Files, Path, Paths }
import java.nio.file.attribute.BasicFileAttributes
import java.nio.ByteBuffer
import scala.util.Try
import com.github.benmanes.caffeine.cache.{ Cache, Caffeine }
import sbt.io.IO
import sjsonnew.{ FileIsoStringLongs, HashUtil, IsoStringLong }

/**
 * Overrides sjson-new's `File`/`Path` isos so the sha256 content hash they embed is memoized by
 * (path, size, lastModified) instead of re-read from disk on every serialization. Same
 * invalidation as the digest cache in sbt.util.CacheImplicits (sbt/sbt#8363): a changed file
 * changes its size or timestamp and is re-hashed.
 *
 * Mix in after `BasicJsonProtocol` (via `contrabandCodecParents`) so these overrides win in
 * linearization.
 */
trait MemoizedFileFormats extends FileIsoStringLongs {
  import MemoizedFileFormats.{ Entry, hashCache }

  // sjsonnew.HashUtil.sha256ToLong is private[sjsonnew]; same semantics via the public sha256
  private def hashFile(path: Path): Long = {
    val buf = HashUtil.sha256(path.toFile())
    if buf.length < 8 then 0L else ByteBuffer.wrap(buf).getLong()
  }

  private def memoizedHash(path: Path): Long =
    Try(Files.readAttributes(path, classOf[BasicFileAttributes])).toOption match {
      case None =>
        // no size/mtime to validate a cache entry against, so hash uncached
        if Files.isRegularFile(path) then hashFile(path) else 0L
      case Some(attrs) =>
        val key = path.toString
        val mtime = attrs.lastModifiedTime.toMillis
        val cached = hashCache.getIfPresent(key)
        if (cached != null && cached.size == attrs.size && cached.mtime == mtime) cached.hash
        else {
          val h = if (attrs.isDirectory) 0L else hashFile(path)
          // 0L is the sentinel for directories and files that vanish mid-hash, not a
          // content hash; caching it would keep serving 0 for the key
          if (h != 0L) hashCache.put(key, new Entry(attrs.size, mtime, h))
          h
        }
    }

  override implicit lazy val fileStringLongIso: IsoStringLong[File] = IsoStringLong.iso[File](
    file => (IO.toURI(file).toASCIIString, memoizedHash(file.toPath())),
    p => IO.toFile(new URI(p._1))
  )

  override implicit lazy val pathStringLongIso: IsoStringLong[Path] = IsoStringLong.iso[Path](
    path => (path.toString, memoizedHash(path)),
    p => Paths.get(p._1)
  )
}

private[librarymanagement] object MemoizedFileFormats {
  private final class Entry(val size: Long, val mtime: Long, val hash: Long)

  // shared by every mix-in of the trait; a per-instance cache would re-hash the same
  // files for each codec object that extends MemoizedFileFormats
  private val hashCache: Cache[String, Entry] =
    Caffeine.newBuilder().maximumSize(65536).build[String, Entry]()
}
