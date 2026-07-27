/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.librarymanagement

import java.io.File
import java.net.URI
import scala.util.Try
import sjsonnew.{ Builder, IsoStringLong, JsonFormat, Unbuilder, deserializationError }
import sbt.io.IO
import sbt.util.CacheStore
import sbt.librarymanagement.*

final case class UpdateReportCache(
    lite: UpdateReportLite,
    stats: UpdateStats,
    stamps: Map[String, Long],
    cachedDescriptor: File
)

object UpdateReportPersistence:

  /**
   * The generated library-management codecs, with the artifact content hash disabled. Persisted update
   * reports are the only thing that uses them; everything else keeps the stock `LibraryManagementCodec`
   * object, including the `inputs` store, so `Tracked.inputChanged` still hashes contents for
   * invalidation.
   *
   * sjsonnew serializes a `File` as a `(uri, Long)` pair whose Long is
   * `HashUtil.sha256ToLong(file.toPath())` -- a full content hash of the file. Nothing reads it back:
   * `IsoStringLong[File].from` parses the URI and drops the Long, and `update` decides staleness in
   * `LibraryManagement.fileUptodate`, which checks `File.exists` and the modification time against
   * `UpdateReport.stamps`. Meanwhile a report names an artifact once per configuration it resolved in,
   * and the projects of a build largely share their dependencies, so writing the caches re-reads the
   * whole downloaded classpath many times over -- easily the dominant cost of writing them -- to produce
   * bytes no reader looks at.
   *
   * `fileStringLongIso` is an `implicit lazy val` in `sjsonnew.FileIsoStringLongs`, so it is a virtual
   * member and every generated format resolves `JsonFormat[File]` as
   * `isoStringLongFormat[File](fileStringLongIso)` through its self-type. Overriding it here therefore
   * also reaches the `Vector[(Artifact, File)]` nested inside the generated `ModuleReportFormat`, which
   * a locally-scoped `JsonFormat[File]` could not.
   *
   * The JSON shape is unchanged -- only the Long's value is -- so caches stay readable by sbt versions
   * that still write the hash, and the ones written here stay readable by them.
   */
  private[sbt] object CacheCodec extends LibraryManagementCodec:

    /** `IO.toURI` emits the same text the stock iso puts in `first`, and `IO.toFile` inverts it. */
    override implicit lazy val fileStringLongIso: IsoStringLong[File] =
      IsoStringLong.iso[File](
        (f: File) => (IO.toURI(f).toASCIIString, 0L),
        (p: (String, Long)) => IO.toFile(new URI(p._1))
      )

  end CacheCodec

  // Not the stock `LibraryManagementCodec`: see `CacheCodec` for why persisted reports must not
  // content-hash the artifacts they name.
  import CacheCodec.given

  given updateReportCacheFormat: JsonFormat[UpdateReportCache] =
    new JsonFormat[UpdateReportCache]:
      override def read[J](
          jsOpt: Option[J],
          unbuilder: Unbuilder[J]
      ): UpdateReportCache =
        jsOpt match
          case Some(js) =>
            unbuilder.beginObject(js)
            val lite = unbuilder.readField[UpdateReportLite]("lite")
            val stats = unbuilder.readField[UpdateStats]("stats")
            val stamps = unbuilder.readField[Map[String, Long]]("stamps")
            val cachedDescriptor = unbuilder.readField[File]("cachedDescriptor")
            unbuilder.endObject()
            UpdateReportCache(lite, stats, stamps, cachedDescriptor)
          case None =>
            deserializationError("Expected JsObject but found None")

      override def write[J](obj: UpdateReportCache, builder: Builder[J]): Unit =
        builder.beginObject()
        builder.addField("lite", obj.lite)
        builder.addField("stats", obj.stats)
        builder.addField("stamps", obj.stamps)
        builder.addField("cachedDescriptor", obj.cachedDescriptor)
        builder.endObject()

  def toCache(ur: UpdateReport): UpdateReportCache =
    UpdateReportCache(
      lite = JsonUtil.toLite(ur),
      stats = ur.stats,
      stamps = ur.stamps,
      cachedDescriptor = ur.cachedDescriptor
    )

  def fromCache(cache: UpdateReportCache): UpdateReport =
    JsonUtil
      .fromLiteFull(cache.lite, cache.cachedDescriptor)
      .withStats(cache.stats)
      .withStamps(cache.stamps)

  def readFrom(store: CacheStore): Option[UpdateReportCache] =
    Try(store.read[UpdateReportCache]()).toOption
      .orElse(
        Try(store.read[UpdateReport]()).toOption
          .map(toCache)
      )

  def writeTo(store: CacheStore, cache: UpdateReportCache): Unit =
    store.write(cache)

end UpdateReportPersistence
