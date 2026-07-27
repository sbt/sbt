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

  /** The shape before this one, a whole serialized `UpdateReport`, carries no version. */
  private final val FormatVersion = 1

  private final case class IndexedDetail(
      organization: String,
      name: String,
      modules: Vector[Int]
  )

  /** One configuration's details, in the order the configuration resolved them. */
  private final case class IndexedConfig(configuration: String, details: Vector[IndexedDetail])

  private given indexedDetailFormat: JsonFormat[IndexedDetail] = new JsonFormat[IndexedDetail]:
    def write[J](obj: IndexedDetail, builder: Builder[J]): Unit =
      builder.beginObject()
      builder.addField("organization", obj.organization)
      builder.addField("name", obj.name)
      builder.addField("modules", obj.modules)
      builder.endObject()
    def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): IndexedDetail = jsOpt match
      case Some(js) =>
        unbuilder.beginObject(js)
        val organization = unbuilder.readField[String]("organization")
        val name = unbuilder.readField[String]("name")
        val modules = unbuilder.readField[Vector[Int]]("modules")
        unbuilder.endObject()
        IndexedDetail(organization, name, modules)
      case None => deserializationError("Expected JsObject but found None")

  private given indexedConfigFormat: JsonFormat[IndexedConfig] = new JsonFormat[IndexedConfig]:
    def write[J](obj: IndexedConfig, builder: Builder[J]): Unit =
      builder.beginObject()
      builder.addField("configuration", obj.configuration)
      builder.addField("details", obj.details)
      builder.endObject()
    def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): IndexedConfig = jsOpt match
      case Some(js) =>
        unbuilder.beginObject(js)
        val configuration = unbuilder.readField[String]("configuration")
        val details = unbuilder.readField[Vector[IndexedDetail]]("details")
        unbuilder.endObject()
        IndexedConfig(configuration, details)
      case None => deserializationError("Expected JsObject but found None")

  private final class ModuleTable:
    private val table = Vector.newBuilder[ModuleReport]
    private val byIdentity = new java.util.IdentityHashMap[ModuleReport, Integer]()
    private val byValue = new java.util.HashMap[ModuleReport, Integer]()
    private var assigned = 0

    def indexOf(mr: ModuleReport): Int =
      byIdentity.get(mr) match
        case null =>
          val index =
            byValue.get(mr) match
              case null =>
                val fresh = assigned
                assigned += 1
                table += mr
                byValue.put(mr, fresh)
                fresh
              case shared => shared.intValue
          byIdentity.put(mr, index)
          index
        case hit => hit.intValue

    def result(): Vector[ModuleReport] = table.result()

  private def writeV1[J](obj: UpdateReportCache, builder: Builder[J]): Unit =
    val modules = new ModuleTable
    val configurations = obj.lite.configurations.map: cr =>
      IndexedConfig(
        cr.configuration,
        cr.details.map(oar =>
          IndexedDetail(oar.organization, oar.name, oar.modules.map(modules.indexOf))
        )
      )
    builder.beginObject()
    builder.addField("version", FormatVersion)
    // Filled by the traversal above, so it has to be rendered after it.
    builder.addField("modules", modules.result())
    builder.addField("configurations", configurations)
    builder.addField("stats", obj.stats)
    builder.addField("stamps", obj.stamps)
    builder.addField("cachedDescriptor", obj.cachedDescriptor)
    builder.endObject()

  /** Reads the fields of an already-open v1 object; the caller owns `beginObject`/`endObject`. */
  private def readV1[J](unbuilder: Unbuilder[J]): UpdateReportCache =
    // `readFrom` turns this into a miss, so a newer cache re-resolves instead of being misread.
    val version = unbuilder.readField[Int]("version")
    if version != FormatVersion then
      deserializationError(s"Expected update cache version $FormatVersion but found $version")
    // Not a `JsonFormat[ModuleReport]` member: the wildcard `CacheCodec.given` import is in this
    // same scope, so one would be ambiguous with it.
    val modules =
      unbuilder.readField[Vector[ModuleReport]]("modules").map(UpdateReportInterner.intern)
    val configurations = unbuilder.readField[Vector[IndexedConfig]]("configurations")
    val stats = unbuilder.readField[UpdateStats]("stats")
    val stamps = unbuilder.readField[Map[String, Long]]("stamps")
    val cachedDescriptor = unbuilder.readField[File]("cachedDescriptor")
    val shared = new java.util.HashMap[(String, String, Vector[Int]), OrganizationArtifactReport]()
    def detailFor(d: IndexedDetail): OrganizationArtifactReport =
      val key = (d.organization, d.name, d.modules)
      shared.get(key) match
        case null =>
          val fresh = OrganizationArtifactReport(d.organization, d.name, d.modules.map(modules))
          shared.put(key, fresh)
          fresh
        case hit => hit
    UpdateReportCache(
      UpdateReportLite(
        configurations.map(cfg =>
          ConfigurationReportLite(cfg.configuration, cfg.details.map(detailFor))
        )
      ),
      stats,
      stamps,
      cachedDescriptor
    )

  /**
   * `lookupField` consumes nothing, so the branch readers below still see every field. Each branch
   * demands a field its own shape must have: sjson-new decodes a missing array as an empty one rather
   * than failing, so a cache of any other shape would read back as a report with no configurations --
   * which `update` accepts as up to date and hands back as an empty managed classpath.
   */
  given updateReportCacheFormat: JsonFormat[UpdateReportCache] =
    new JsonFormat[UpdateReportCache]:
      override def read[J](
          jsOpt: Option[J],
          unbuilder: Unbuilder[J]
      ): UpdateReportCache =
        jsOpt match
          case Some(js) =>
            unbuilder.beginObject(js)
            def demand(names: String*): Unit =
              names
                .find(unbuilder.lookupField(_).isEmpty)
                .foreach: missing =>
                  deserializationError(s"Not an update cache: no `$missing` field")
            val cache =
              if unbuilder.lookupField("version").isDefined then
                demand("modules", "configurations")
                readV1(unbuilder)
              else
                demand("configurations")
                // The generated reader opens its own context on the same object, which is legal
                // while this one is open.
                internModules(toCache(CacheCodec.UpdateReportFormat.read(jsOpt, unbuilder)))
            unbuilder.endObject()
            cache
          case None =>
            deserializationError("Expected JsObject but found None")

      override def write[J](obj: UpdateReportCache, builder: Builder[J]): Unit =
        writeV1(obj, builder)

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

  def writeTo(store: CacheStore, cache: UpdateReportCache): Unit =
    store.write(cache)

end UpdateReportPersistence
