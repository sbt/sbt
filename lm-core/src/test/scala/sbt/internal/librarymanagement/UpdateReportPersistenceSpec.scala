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
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sbt.io.IO
import sbt.util.CacheStore
import sbt.librarymanagement.*
import sbt.librarymanagement.syntax.*
import sbt.librarymanagement.LibraryManagementCodec.given

class UpdateReportPersistenceSpec extends AnyFlatSpec with Matchers:

  def buildTestReport(baseDir: File): UpdateReport =
    val epochCalendar = Calendar.getInstance()
    epochCalendar.setTimeInMillis(0L)

    val modId = ModuleID("org.example", "test-module", "1.0.0")
    val artifact = Artifact("test-module", "jar", "jar", None, Vector.empty, None, Map.empty, None)
    val jarFile = new File(baseDir, "test-module.jar")
    IO.touch(jarFile)

    val moduleReport = ModuleReport(
      modId,
      Vector((artifact, jarFile)),
      Vector.empty,
      None,
      Some(epochCalendar),
      Some("maven-central"),
      Some("maven-central"),
      false,
      None,
      None,
      None,
      None,
      Map.empty,
      Some(true),
      None,
      Vector(ConfigRef("compile")),
      Vector.empty,
      Vector.empty
    )

    val details = Vector(
      OrganizationArtifactReport("org.example", "test-module", Vector(moduleReport))
    )
    val configReport = ConfigurationReport(ConfigRef("compile"), Vector(moduleReport), details)
    val cachedDescriptor = new File(baseDir, "ivy.xml")
    IO.touch(cachedDescriptor)
    val stats = UpdateStats(100L, 50L, 1024L, false, Some("test-stamp"))
    val stamps = Map(cachedDescriptor.getAbsolutePath -> 12345L)

    UpdateReport(cachedDescriptor, Vector(configReport), stats, stamps)

  "UpdateReportPersistence.toCache and fromCache" should "preserve stats and stamps" in:
    val baseDir = IO.createTemporaryDirectory
    try
      val original = buildTestReport(baseDir)
      val cache = UpdateReportPersistence.toCache(original)
      val restored = UpdateReportPersistence.fromCache(cache)

      restored.stats.resolveTime.shouldBe(original.stats.resolveTime)
      restored.stats.downloadTime.shouldBe(original.stats.downloadTime)
      restored.stats.downloadSize.shouldBe(original.stats.downloadSize)
      restored.stats.stamp.shouldBe(original.stats.stamp)
      restored.stamps.shouldBe(original.stamps)
      restored.cachedDescriptor.shouldBe(original.cachedDescriptor)
    finally IO.delete(baseDir)

  it should "preserve all modules without filtering" in:
    val baseDir = IO.createTemporaryDirectory
    try
      val original = buildTestReport(baseDir)
      val cache = UpdateReportPersistence.toCache(original)
      val restored = UpdateReportPersistence.fromCache(cache)

      restored.configurations.size.shouldBe(original.configurations.size)
      restored.configurations.head.modules.size.shouldBe(original.configurations.head.modules.size)
      restored.allFiles.size.shouldBe(original.allFiles.size)
    finally IO.delete(baseDir)

  it should "preserve modules when details are stripped from the report" in:
    val baseDir = IO.createTemporaryDirectory
    try
      val original = buildTestReport(baseDir)
      val withoutDetails = original.withConfigurations(
        original.configurations.map(_.withDetails(Vector.empty))
      )
      val cache = UpdateReportPersistence.toCache(withoutDetails)
      val restored = UpdateReportPersistence.fromCache(cache)

      restored.configurations.size.shouldBe(withoutDetails.configurations.size)
      restored.configurations.head.modules.size
        .shouldBe(withoutDetails.configurations.head.modules.size)
      restored.allFiles.size.shouldBe(withoutDetails.allFiles.size)
    finally IO.delete(baseDir)

  "UpdateReportPersistence.readFrom and writeTo" should "round-trip correctly" in:
    val baseDir = IO.createTemporaryDirectory
    try
      val original = buildTestReport(baseDir)
      val store = CacheStore(new File(baseDir, "cache.json"))

      UpdateReportPersistence.writeTo(store, UpdateReportPersistence.toCache(original))
      val readBack = UpdateReportPersistence.readFrom(store)

      readBack.isDefined.shouldBe(true)
      val restored = UpdateReportPersistence.fromCache(readBack.get)
      restored.stats.stamp.shouldBe(original.stats.stamp)
      restored.stamps.shouldBe(original.stamps)
    finally IO.delete(baseDir)

  it should "return None for missing cache file" in:
    val baseDir = IO.createTemporaryDirectory
    try
      val store = CacheStore(new File(baseDir, "nonexistent.json"))
      val result = UpdateReportPersistence.readFrom(store)
      result.shouldBe(None)
    finally IO.delete(baseDir)

  it should "fall back to legacy UpdateReport format" in:
    val baseDir = IO.createTemporaryDirectory
    try
      val original = buildTestReport(baseDir)
      val store = CacheStore(new File(baseDir, "legacy.json"))

      store.write(original)

      val readBack = UpdateReportPersistence.readFrom(store)
      readBack.isDefined.shouldBe(true)

      val restored = UpdateReportPersistence.fromCache(readBack.get)
      restored.configurations.size.shouldBe(original.configurations.size)
    finally IO.delete(baseDir)

  "UpdateReportPersistenceBenchmark" should "run and return valid result" in:
    val result = UpdateReportPersistenceBenchmark.run(
      iterations = 10,
      configs = Seq("compile"),
      modulesPerConfig = 5,
      warmupIterations = 2
    )

    result.isRight.shouldBe(true)
    val benchResult = result.toOption.get
    benchResult.iterationCount.shouldBe(10)
    benchResult.fullFormatMs.should(be >= 0L)
    benchResult.cacheFormatMs.should(be >= 0L)
    benchResult.fullSizeBytes.should(be > 0L)
    benchResult.cacheSizeBytes.should(be > 0L)

  it should "format result correctly" in:
    val result = UpdateReportPersistenceBenchmark.run(
      iterations = 5,
      configs = Seq("compile"),
      modulesPerConfig = 3,
      warmupIterations = 1
    )

    result.isRight.shouldBe(true)
    val formatted = UpdateReportPersistenceBenchmark.formatResult(result.toOption.get)
    formatted.should(include("Full format"))
    formatted.should(include("Cache format"))
    formatted.should(include("Ratio"))

  it should "reject invalid inputs" in:
    UpdateReportPersistenceBenchmark
      .run(iterations = 0)
      .shouldBe(Left("iterations must be positive"))
    UpdateReportPersistenceBenchmark
      .run(iterations = 1, configs = Seq.empty)
      .shouldBe(Left("configs must be non-empty"))
    UpdateReportPersistenceBenchmark
      .run(iterations = 1, modulesPerConfig = 0)
      .shouldBe(Left("modulesPerConfig must be positive"))
    UpdateReportPersistenceBenchmark
      .run(iterations = 1, warmupIterations = -1)
      .shouldBe(Left("warmupIterations must be non-negative"))

end UpdateReportPersistenceSpec
