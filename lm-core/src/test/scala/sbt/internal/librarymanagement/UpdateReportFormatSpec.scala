/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.librarymanagement

import java.io.File
import java.util.Calendar
import sbt.io.IO
import sbt.librarymanagement.*
import sbt.util.{ CacheStore, CacheStoreFactory }

object UpdateReportFormatSpec extends verify.BasicTestSuite:

  test("a v1 round trip preserves the whole report by value"):
    IO.withTemporaryDirectory: dir =>
      val original = fixture(dir)
      val readBack = roundTrip(dir, "v1.json", original)
      assert(readBack.cachedDescriptor == original.cachedDescriptor)
      assert(readBack.stamps == original.stamps)
      assert(readBack.stats == original.stats)
      assert(
        readBack.configurations.map(_.configuration) == original.configurations.map(_.configuration)
      )
      assert(
        readBack.configurations.map(_.modules) == original.configurations.map(_.modules),
        "every module of every configuration must come back value-equal, in order"
      )

  test("a v1 cache writes a version marker and each distinct module report once"):
    IO.withTemporaryDirectory: dir =>
      val file = new File(dir, "v1.json")
      UpdateReportPersistence
        .writeTo(CacheStore(file), UpdateReportPersistence.toCache(fixture(dir)))
      val json = IO.read(file)
      assert(json.contains("\"version\":1"), s"expected a v1 marker in $json")
      // 2 table entries, plus 2 configurations naming 2 detail groups each.
      assert(
        occurrences(json, "\"organization\":\"org.example\"") == 6,
        s"expected 2 table entries and 4 detail groups, got $json"
      )

  test("per-configuration module order survives the round trip"):
    IO.withTemporaryDirectory: dir =>
      val original = fixture(dir)
      val readBack = roundTrip(dir, "order.json", original)
      val names = (ur: UpdateReport) => ur.configurations.map(_.modules.map(_.module.name))
      assert(names(original).head == Vector("zebra-lib", "alpha-lib"), "fixture must not be sorted")
      assert(names(readBack) == names(original), "classpath order comes from this order")

  // The fixture above leaves `details` empty, so the two orders coincide there.
  test("module order survives when details group differently than the resolver ordered them"):
    IO.withTemporaryDirectory: dir =>
      val descriptor = new File(dir, "ivy.xml")
      IO.touch(descriptor)
      def moduleReport(name: String, version: String): ModuleReport =
        val jar = new File(dir, s"$name-$version.jar")
        IO.touch(jar)
        val artifact = Artifact(name, "jar", "jar", None, Vector.empty, None, Map.empty, None)
        ModuleReport(ModuleID("org." + name, name, version), Vector((artifact, jar)), Vector.empty)
          .withConfigurations(Vector(ConfigRef("compile")))
      val a1 = moduleReport("a", "1.0.0")
      val a2 = moduleReport("a", "2.0.0")
      val b1 = moduleReport("b", "1.0.0")
      val details = Vector(
        OrganizationArtifactReport("org.a", "a", Vector(a1, a2)),
        OrganizationArtifactReport("org.b", "b", Vector(b1))
      )
      val resolved = Vector(a1, b1, a2)
      val original = UpdateReport(
        descriptor,
        Vector(ConfigurationReport(ConfigRef("compile"), resolved, details)),
        UpdateStats(0L, 0L, 0L, false),
        Map.empty
      )
      val readBack = roundTrip(dir, "interleaved.json", original)
      assert(
        readBack.configurations.head.modules.map(_.module) == resolved.map(_.module),
        "flattening the details would give a, a, b"
      )

  test("a module report used by two configurations reads back as one instance"):
    IO.withTemporaryDirectory: dir =>
      val ur = roundTrip(dir, "shared.json", fixture(dir))
      assert(
        ur.configurations(0).modules.head eq ur.configurations(1).modules.head,
        "the module table must be referenced, not copied"
      )
      assert(
        ur.configurations(0).details.head eq ur.configurations(1).details.head,
        "the OrganizationArtifactReport around it should be shared too"
      )

  test("a report carrying a publicationDate is deduplicated like any other"):
    // Sharing the slot adds no aliasing: `toLite` already puts one `Option[Calendar]` in every
    // configuration.
    IO.withTemporaryDirectory: dir =>
      val epoch = Calendar.getInstance()
      epoch.setTimeInMillis(0L)
      val dated = fixture(dir, publicationDate = Some(epoch))
      assert(
        dated.configurations(0).modules.head.publicationDate.nonEmpty,
        "fixture should carry a publicationDate"
      )
      val ur = roundTrip(dir, "dated.json", dated)
      val first = ur.configurations(0).modules.head
      val second = ur.configurations(1).modules.head
      assert(first.publicationDate.nonEmpty, "the date must survive the round trip")
      assert(first eq second, "a dated report should still be written once and referenced twice")
      assert(
        first.publicationDate.get.getTimeInMillis == 0L,
        "the calendar must round trip to the same instant"
      )

  test("separate v1 reads share one instance per distinct module"):
    // The table only shares within one report; interning is what makes two parses converge.
    IO.withTemporaryDirectory: dir =>
      val original = fixture(dir)
      val first = roundTrip(dir, "shared-a.json", original).configurations.head.modules.head
      val second = roundTrip(dir, "shared-b.json", original).configurations.head.modules.head
      assert(first eq second, "the v1 module table must be interned as it is decoded")

  test("a dated report is still not pooled across separate reads"):
    IO.withTemporaryDirectory: dir =>
      val epoch = Calendar.getInstance()
      epoch.setTimeInMillis(0L)
      val dated = fixture(dir, publicationDate = Some(epoch))
      val first = roundTrip(dir, "dated-a.json", dated).configurations.head.modules.head
      val second = roundTrip(dir, "dated-b.json", dated).configurations.head.modules.head
      assert(first ne second, "pooling would alias a mutable calendar across reports")
      assert(first == second, "the two instances must still be value-equal")
      assert(first.module eq second.module, "the immutable coordinate inside is still interned")

  test("toCache preserves the instance sharing it was handed"):
    // `toLite` used to rebuild every report at every position, so no sharing reached the writer.
    IO.withTemporaryDirectory: dir =>
      val cache = UpdateReportPersistence.toCache(fixture(dir))
      val first = cache.lite.configurations(0).details.head.modules.head
      val second = cache.lite.configurations(1).details.head.modules.head
      assert(first eq second, "toLite must pass shared module reports through, not clone them")

  test("a legacy full-report cache still reads"):
    IO.withTemporaryDirectory: dir =>
      val original = fixture(dir)
      val store = CacheStore(new File(dir, "legacy.json"))
      store.write(original)(using LibraryManagementCodec.UpdateReportFormat)
      val ur = UpdateReportPersistence.fromCache(
        UpdateReportPersistence
          .readFrom(store)
          .getOrElse(sys.error("expected a cache"))
      )
      assert(
        ur.configurations.map(_.modules.map(_.module.name)) ==
          original.configurations.map(_.modules.map(_.module.name))
      )
      assert(
        ur.configurations(0).modules.head eq ur.configurations(1).modules.head,
        "the legacy fallback must intern too"
      )

  test("a cache claiming a newer version is a miss, not a misread"):
    IO.withTemporaryDirectory: dir =>
      val file = new File(dir, "newer.json")
      UpdateReportPersistence.writeTo(
        CacheStore(file),
        UpdateReportPersistence.toCache(fixture(dir))
      )
      IO.write(file, IO.read(file).replace("\"version\":1", "\"version\":2"))
      assert(UpdateReportPersistence.readFrom(CacheStore(file)).isEmpty)

  test("a lite-shaped cache is a miss, not an empty report"):
    // `develop` writes this shape today. Every field the generated `UpdateReport` reader needs is
    // present except `configurations`, and a missing array decodes to an empty one -- so without a
    // positive discriminator this reads back as a report with no modules, which `update` accepts.
    IO.withTemporaryDirectory: dir =>
      val store = CacheStore(new File(dir, "lite.json"))
      store.write(UpdateReportPersistence.toCache(fixture(dir)))(using liteCacheFormat)
      assert(UpdateReportPersistence.readFrom(store).isEmpty)

  test("a module and its order entry are one table entry, not two"):
    // `toLite` reorders a module's callers when it drops the artificial ones. The order has to carry
    // the same normalization, or the module is value-distinct from its own order entry.
    IO.withTemporaryDirectory: dir =>
      val file = new File(dir, "callers.json")
      val original = withArtificialCallers(dir)
      UpdateReportPersistence
        .writeTo(CacheStore(file), UpdateReportPersistence.toCache(original))
      assert(
        occurrences(IO.read(file), "\"organization\":\"org.example\"") == 2,
        s"expected 1 table entry and 1 detail group, got ${IO.read(file)}"
      )
      val cr = roundTrip(dir, "callers-rt.json", original).configurations.head
      assert(
        cr.modules.head eq cr.details.head.modules.head,
        "the restored modules and details must be the same instances"
      )

  test("an uninterleaved configuration stores no separate order"):
    IO.withTemporaryDirectory: dir =>
      val file = new File(dir, "identity.json")
      UpdateReportPersistence
        .writeTo(CacheStore(file), UpdateReportPersistence.toCache(fixture(dir)))
      val json = IO.read(file)
      assert(
        occurrences(json, "\"configuration\"") == occurrences(json, "\"details\""),
        s"every configuration should carry details, got $json"
      )
      assert(
        occurrences(json, "\"modules\"") == occurrences(json, "\"details\"") * 2 + 1,
        s"expected only the table and the per-detail indices, got $json"
      )

  test("a configuration that resolved nothing does not come back with the details flattened"):
    // The elision is "absent means flattened". An order that is present and empty has to stay empty,
    // which an empty array cannot say on its own -- hence the `lookupField` on the read side.
    IO.withTemporaryDirectory: dir =>
      val descriptor = new File(dir, "ivy.xml")
      IO.touch(descriptor)
      val jar = new File(dir, "evicted.jar")
      IO.touch(jar)
      val artifact = Artifact("evicted", "jar", "jar", None, Vector.empty, None, Map.empty, None)
      val evicted =
        ModuleReport(
          ModuleID("org.example", "evicted", "1.0.0"),
          Vector((artifact, jar)),
          Vector.empty
        )
          .withConfigurations(Vector(ConfigRef("compile")))
      val details = Vector(OrganizationArtifactReport("org.example", "evicted", Vector(evicted)))
      val original = UpdateReport(
        descriptor,
        Vector(ConfigurationReport(ConfigRef("compile"), Vector.empty, details)),
        UpdateStats(0L, 0L, 0L, false),
        Map.empty
      )
      val readBack = roundTrip(dir, "empty-order.json", original)
      assert(readBack.configurations.head.details.size == 1, "the details must survive")
      assert(
        readBack.configurations.head.modules.isEmpty,
        s"expected no modules, got ${readBack.configurations.head.modules.map(_.module)}"
      )

  test("a compressed store round trips a report"):
    // The production wiring is `cacheStoreFactory.makeCompressed("output")`; the plain store the other
    // tests use would not catch a framing mistake.
    IO.withTemporaryDirectory: dir =>
      val original = fixture(dir)
      val store = CacheStoreFactory.directory(dir).makeCompressed("output")
      UpdateReportPersistence.writeTo(store, UpdateReportPersistence.toCache(original))
      val magic = IO.readBytes(new File(dir, "output")).take(2)
      assert(magic(0) == 0x1f.toByte && magic(1) == 0x8b.toByte, "expected gzip framing")
      val ur = UpdateReportPersistence.fromCache(
        UpdateReportPersistence
          .readFrom(store)
          .getOrElse(sys.error("expected a cache"))
      )
      assert(
        ur.configurations.map(_.modules.map(_.module.name)) ==
          original.configurations.map(_.modules.map(_.module.name))
      )

  test("a v1 cache is a miss for a reader that only knows the full-report shape"):
    IO.withTemporaryDirectory: dir =>
      val file = new File(dir, "v1.json")
      UpdateReportPersistence
        .writeTo(CacheStore(file), UpdateReportPersistence.toCache(fixture(dir)))
      val read = scala.util.Try(
        CacheStore(file).read[UpdateReport]()(using LibraryManagementCodec.UpdateReportFormat)
      )
      assert(read.isFailure, s"a previous sbt must not misread a v1 cache: $read")

  test("readFrom returns None for a file that is not a report at all"):
    IO.withTemporaryDirectory: dir =>
      val file = new File(dir, "junk.json")
      IO.write(file, """{"unrelated":true}""")
      assert(UpdateReportPersistence.readFrom(CacheStore(file)).isEmpty)

  /** The unversioned shape `develop` writes; production never writes it any more. */
  private lazy val liteCacheFormat: sjsonnew.JsonFormat[UpdateReportCache] =
    new sjsonnew.JsonFormat[UpdateReportCache]:
      import sbt.librarymanagement.LibraryManagementCodec.given
      def write[J](obj: UpdateReportCache, builder: sjsonnew.Builder[J]): Unit =
        builder.beginObject()
        builder.addField("lite", obj.lite)
        builder.addField("stats", obj.stats)
        builder.addField("stamps", obj.stamps)
        builder.addField("cachedDescriptor", obj.cachedDescriptor)
        builder.endObject()
      def read[J](jsOpt: Option[J], unbuilder: sjsonnew.Unbuilder[J]): UpdateReportCache =
        sjsonnew.deserializationError("write-only")

  /** One module whose callers survive `filterOutArtificialCallers` only in a different order. */
  private def withArtificialCallers(dir: File): UpdateReport =
    val jar = new File(dir, "lib.jar")
    IO.touch(jar)
    val descriptor = new File(dir, "ivy.xml")
    IO.touch(descriptor)
    def caller(org: String) =
      Caller(ModuleID(org, "c", "1.0.0"), Vector.empty, Map.empty, false, false, true, false)
    val artifact = Artifact("lib", "jar", "jar", None, Vector.empty, None, Map.empty, None)
    val mr =
      ModuleReport(ModuleID("org.example", "lib", "1.0.0"), Vector((artifact, jar)), Vector.empty)
        .withConfigurations(Vector(ConfigRef("compile")))
        .withCallers(Vector(caller("org.real"), caller("org.scala-sbt.temp")))
    val details = Vector(OrganizationArtifactReport("org.example", "lib", Vector(mr)))
    UpdateReport(
      descriptor,
      Vector(ConfigurationReport(ConfigRef("compile"), Vector(mr), details)),
      UpdateStats(0L, 0L, 0L, false),
      Map.empty
    )

  private def roundTrip(dir: File, name: String, ur: UpdateReport): UpdateReport =
    val store = CacheStore(new File(dir, name))
    UpdateReportPersistence.writeTo(store, UpdateReportPersistence.toCache(ur))
    UpdateReportPersistence.fromCache(
      UpdateReportPersistence.readFrom(store).getOrElse(sys.error("expected a cache"))
    )

  private def occurrences(haystack: String, needle: String): Int =
    haystack.sliding(needle.length).count(_ == needle)

  /** Two modules, deliberately not in alphabetical order, in two configurations. */
  private def fixture(dir: File, publicationDate: Option[Calendar] = None): UpdateReport =
    val descriptor = new File(dir, "ivy.xml")
    IO.touch(descriptor)
    def moduleReport(name: String): ModuleReport =
      val jar = new File(dir, s"$name.jar")
      IO.touch(jar)
      val artifact = Artifact(name, "jar", "jar", None, Vector.empty, None, Map.empty, None)
      ModuleReport(ModuleID("org.example", name, "1.0.0"), Vector((artifact, jar)), Vector.empty)
        .withConfigurations(Vector(ConfigRef("compile"), ConfigRef("test")))
        .withPublicationDate(publicationDate)
    val modules = Vector(moduleReport("zebra-lib"), moduleReport("alpha-lib"))
    def configReport(name: String) = ConfigurationReport(ConfigRef(name), modules, Vector.empty)
    UpdateReport(
      descriptor,
      Vector(configReport("compile"), configReport("test")),
      UpdateStats(100L, 50L, 1024L, false, Some("stamp")),
      Map(descriptor.getAbsolutePath -> 12345L)
    )
