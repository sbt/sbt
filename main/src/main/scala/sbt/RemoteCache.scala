/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.io.File
import java.nio.file.Path

import sbt.Keys.*
import sbt.librarymanagement.*
import sbt.util.{ CacheImplicits, DiskActionCacheStore }
import sjsonnew.JsonFormat

object RemoteCache:
  private[sbt] def artifactToStr(art: Artifact): String = {
    import LibraryManagementCodec.given
    import sjsonnew.support.scalajson.unsafe.*
    val format: JsonFormat[Artifact] = summon[JsonFormat[Artifact]]
    CompactPrinter(Converter.toJsonUnsafe(art)(using format))
  }

  lazy val defaultCacheLocation: File = SysProp.globalLocalCache

  lazy val globalSettings: Seq[Def.Setting[?]] = Seq(
    localCacheDirectory :== defaultCacheLocation,
    localDigestCacheByteSize :== CacheImplicits.defaultLocalDigestCacheByteSize,
    cacheVersion :== sys.props.get("sbt.cacheversion").flatMap(_.toLongOption).getOrElse(0L),
    rootOutputDirectory := {
      appConfiguration.value.baseDirectory
        .toPath()
        .resolve("target")
        .resolve("out")
    },
    cacheStores := {
      val c = fileConverter.value
      List(
        DiskActionCacheStore(localCacheDirectory.value.toPath, c)
      )
    },
    remoteCache := SysProp.remoteCache,
    remoteCacheTlsCertificate := SysProp.remoteCacheTlsCertificate,
    remoteCacheTlsClientCertificate := SysProp.remoteCacheTlsClientCertificate,
    remoteCacheTlsClientKey := SysProp.remoteCacheTlsClientKey,
    remoteCacheHeaders := SysProp.remoteCacheHeaders,
  )
end RemoteCache
