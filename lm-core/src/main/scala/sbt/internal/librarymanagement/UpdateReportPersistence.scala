/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.librarymanagement

import java.io.File
import scala.util.Try
import sjsonnew.{ Builder, JsonFormat, Unbuilder, deserializationError }
import sbt.util.CacheStore
import sbt.librarymanagement.*
import sbt.librarymanagement.LibraryManagementCodec.given

final case class UpdateReportCache(
    lite: UpdateReportLite,
    stats: UpdateStats,
    stamps: Map[String, Long],
    cachedDescriptor: File
)

object UpdateReportPersistence:

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
