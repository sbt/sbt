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
   * The generated library-management codecs, with the artifact content hash disabled: nothing reads the
   * hash back, and computing it re-reads the whole downloaded classpath.
   *
   * `fileStringLongIso` is virtual, so overriding it also reaches the `Vector[(Artifact, File)]` nested
   * inside the generated `ModuleReportFormat`.
   */
  private[sbt] object CacheCodec extends LibraryManagementCodec:
    override implicit lazy val fileStringLongIso: IsoStringLong[File] =
      IsoStringLong.iso[File](
        (f: File) => (IO.toURI(f).toASCIIString, 0L),
        (p: (String, Long)) => IO.toFile(new URI(p._1))
      )

  end CacheCodec

  import CacheCodec.given

  /** Interns the modules of a decoded cache, in a pass since the generated reader has no hook. */
  private def internModules(cache: UpdateReportCache): UpdateReportCache =
    cache.copy(lite =
      UpdateReportLite(
        cache.lite.configurations.map(cr =>
          ConfigurationReportLite(
            cr.configuration,
            cr.details.map(d =>
              OrganizationArtifactReport(
                d.organization,
                d.name,
                d.modules.map(UpdateReportInterner.intern)
              )
            )
          )
        )
      )
    )

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
            internModules(UpdateReportCache(lite, stats, stamps, cachedDescriptor))
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
          .map(internModules)
      )

  def writeTo(store: CacheStore, cache: UpdateReportCache): Unit =
    store.write(cache)

end UpdateReportPersistence
