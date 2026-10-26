/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.librarymanagement

import java.io.File
import hedgehog.*
import hedgehog.runner.*
import _root_.sbt.io.IO
import _root_.sbt.util.CacheStore
import _root_.sbt.librarymanagement.*
import _root_.sbt.librarymanagement.LibraryManagementCodec.given
import GenUpdateReport.genUpdateReport

object UpdateReportPersistenceSpec extends Properties:
  override def tests: List[Test] = List(
    property(
      "fromCache(toCache(report)) restores the report with artificial callers filtered out",
      propInMemoryRoundTrip
    ),
    property(
      "fromCache(toCache(report)) preserves the resolver's module order",
      propModuleOrder
    ),
    property(
      "fromCache(toCache(report)) ignores whether details were present",
      propDetailsStripped
    ),
    property("writeTo then readFrom matches the in-memory round trip", propStoreRoundTrip),
    property(
      "readFrom falls back to the legacy UpdateReport format",
      propLegacyFallback
    ),
    example("readFrom returns None for a missing cache file", testMissingCacheFile),
    property(
      "UpdateReportPersistenceBenchmark rejects invalid inputs",
      propBenchmarkRejectsInvalid
    ),
    example("UpdateReportPersistenceBenchmark runs and formats its result", testBenchmarkRun),
  )

  def normalize(ur: UpdateReport): UpdateReport =
    ur.withConfigurations(ur.configurations.map: cr =>
      val details =
        if cr.details.nonEmpty then cr.details
        else
          val keys = cr.modules.map(m => (m.module.organization, m.module.name)).distinct
          keys.map: (org, n) =>
            OrganizationArtifactReport(
              org,
              n,
              cr.modules.filter(m => m.module.organization == org && m.module.name == n)
            )
      cr.withModules(cr.modules.map(JsonUtil.withFilteredCallers))
        .withDetails(details.map(d => d.withModules(d.modules.map(JsonUtil.withFilteredCallers)))))

  def restore(ur: UpdateReport): UpdateReport =
    UpdateReportPersistence.fromCache(UpdateReportPersistence.toCache(ur))

  def propInMemoryRoundTrip: Property =
    for ur <- genUpdateReport.forAll
    yield restore(ur) ==== normalize(ur)

  def propModuleOrder: Property =
    for ur <- genUpdateReport.forAll
    yield restore(ur).configurations.map(_.modules.map(_.module)) ====
      ur.configurations.map(_.modules.map(_.module))

  def propDetailsStripped: Property =
    for ur <- genUpdateReport.forAll
    yield
      val stripped = ur.withConfigurations(ur.configurations.map(_.withDetails(Vector.empty)))
      restore(stripped).configurations.map(_.modules) ====
        restore(ur).configurations.map(_.modules)

  def propStoreRoundTrip: Property =
    for ur <- genUpdateReport.forAll
    yield IO.withTemporaryDirectory: dir =>
      val store = CacheStore(new File(dir, "cache.json"))
      UpdateReportPersistence.writeTo(store, UpdateReportPersistence.toCache(ur))
      UpdateReportPersistence.readFrom(store).map(UpdateReportPersistence.fromCache) ====
        Some(restore(ur))

  def propLegacyFallback: Property =
    for ur <- genUpdateReport.forAll
    yield IO.withTemporaryDirectory: dir =>
      val store = CacheStore(new File(dir, "legacy.json"))
      store.write(ur)
      UpdateReportPersistence.readFrom(store).map(UpdateReportPersistence.fromCache) ====
        Some(restore(ur))

  def testMissingCacheFile: Result =
    IO.withTemporaryDirectory: dir =>
      UpdateReportPersistence.readFrom(CacheStore(new File(dir, "nonexistent.json"))) ==== None

  def propBenchmarkRejectsInvalid: Property =
    for
      bad <- Gen.int(Range.linear(-100, 0)).forAll
      badWarmup <- Gen.int(Range.linear(-100, -1)).forAll
      good <- Gen.int(Range.linear(1, 100)).forAll
    yield Result.all(
      List(
        UpdateReportPersistenceBenchmark.run(iterations = bad) ====
          Left("iterations must be positive"),
        UpdateReportPersistenceBenchmark.run(iterations = good, configs = Seq.empty) ====
          Left("configs must be non-empty"),
        UpdateReportPersistenceBenchmark.run(iterations = good, modulesPerConfig = bad) ====
          Left("modulesPerConfig must be positive"),
        UpdateReportPersistenceBenchmark.run(iterations = good, warmupIterations = badWarmup) ====
          Left("warmupIterations must be non-negative"),
      )
    )

  def testBenchmarkRun: Result =
    UpdateReportPersistenceBenchmark.run(
      iterations = 5,
      configs = Seq("compile"),
      modulesPerConfig = 3,
      warmupIterations = 1
    ) match
      case Right(r) =>
        val formatted = UpdateReportPersistenceBenchmark.formatResult(r)
        Result.all(
          List(
            r.iterationCount ==== 5,
            Result.assert(r.fullFormatMs >= 0L),
            Result.assert(r.cacheFormatMs >= 0L),
            Result.assert(r.fullSizeBytes > 0L),
            Result.assert(r.cacheSizeBytes > 0L),
          ) ++ List("Full format", "Cache format", "Ratio").map: s =>
            Result.assert(formatted.contains(s)).log(s"missing $s in $formatted")
        )
      case Left(err) => Result.failure.log(err)
end UpdateReportPersistenceSpec
