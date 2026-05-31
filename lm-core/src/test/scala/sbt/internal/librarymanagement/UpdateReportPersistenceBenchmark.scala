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
import scala.util.Try
import sbt.io.IO
import sbt.util.CacheStore
import sbt.librarymanagement.*
import sbt.librarymanagement.LibraryManagementCodec.given

final case class BenchmarkResult(
    iterationCount: Int,
    fullFormatMs: Long,
    cacheFormatMs: Long,
    fullSizeBytes: Long,
    cacheSizeBytes: Long
):
  def cacheVsFullRatio: Double =
    if fullFormatMs > 0 then cacheFormatMs.toDouble / fullFormatMs else 0.0

  def fullSizeKb: Double = fullSizeBytes / 1024.0
  def cacheSizeKb: Double = cacheSizeBytes / 1024.0

object UpdateReportPersistenceBenchmark:

  def run(
      iterations: Int = 500,
      configs: Seq[String] = Seq("compile", "test"),
      modulesPerConfig: Int = 50,
      warmupIterations: Int = 10
  ): Either[String, BenchmarkResult] =
    for {
      _ <- Either.cond(iterations > 0, (), "iterations must be positive")
      _ <- Either.cond(configs.nonEmpty, (), "configs must be non-empty")
      _ <- Either.cond(modulesPerConfig > 0, (), "modulesPerConfig must be positive")
      _ <- Either.cond(warmupIterations >= 0, (), "warmupIterations must be non-negative")
      baseDir = IO.createTemporaryDirectory
      result <-
        try
          val report = buildSampleReport(baseDir, configs, modulesPerConfig)
          val fullStore = CacheStore(new File(baseDir, "full-format.json"))
          val cacheStore = CacheStore(new File(baseDir, "cache-format.json"))

          fullStore.write(report)
          UpdateReportPersistence.writeTo(cacheStore, UpdateReportPersistence.toCache(report))

          for _ <- 0 until warmupIterations do
            fullStore.read[UpdateReport]()
            UpdateReportPersistence
              .readFrom(cacheStore)
              .map(UpdateReportPersistence.fromCache)
              .getOrElse(sys.error("Expected cache report during warmup"))

          val fullStart = System.currentTimeMillis()
          for _ <- 0 until iterations do fullStore.read[UpdateReport]()
          val fullEnd = System.currentTimeMillis()

          val cacheStart = System.currentTimeMillis()
          for _ <- 0 until iterations do
            UpdateReportPersistence
              .readFrom(cacheStore)
              .map(UpdateReportPersistence.fromCache)
              .getOrElse(sys.error("Expected cache report during benchmark"))
          val cacheEnd = System.currentTimeMillis()

          val fullSize = new File(baseDir, "full-format.json").length()
          val cacheSize = new File(baseDir, "cache-format.json").length()

          Right(
            BenchmarkResult(
              iterationCount = iterations,
              fullFormatMs = fullEnd - fullStart,
              cacheFormatMs = cacheEnd - cacheStart,
              fullSizeBytes = fullSize,
              cacheSizeBytes = cacheSize
            )
          )
        catch case e: Exception => Left(s"Benchmark failed: ${e.getMessage}")
        finally IO.delete(baseDir)
    } yield result

  def buildSampleReport(
      baseDir: File,
      configs: Seq[String],
      modulesPerConfig: Int
  ): UpdateReport =
    val epochCalendar = Calendar.getInstance()
    epochCalendar.setTimeInMillis(0L)

    val configReports = configs
      .map: configName =>
        val moduleReports = (1 to modulesPerConfig)
          .map: i =>
            val modId = ModuleID("org.example", s"module-$i", "1.0.0")
            val artifact =
              Artifact(s"module-$i", "jar", "jar", None, Vector.empty, None, Map.empty, None)
            val jarFile = new File(baseDir, s"$configName/module-$i.jar")
            IO.touch(jarFile)
            ModuleReport(
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
              Vector(ConfigRef(configName)),
              Vector.empty,
              Vector.empty
            )
          .toVector

        val details = Vector(
          OrganizationArtifactReport("org.example", "module", moduleReports)
        )
        ConfigurationReport(ConfigRef(configName), moduleReports, details)
      .toVector

    val cachedDescriptor = new File(baseDir, "ivy.xml")
    IO.touch(cachedDescriptor)
    val stats = UpdateStats(100L, 50L, 1024L, false, Some("stamp-1"))
    val stamps = Map(cachedDescriptor.getAbsolutePath -> System.currentTimeMillis())

    UpdateReport(cachedDescriptor, configReports, stats, stamps)

  def formatResult(result: BenchmarkResult): String =
    f"""UpdateReport Persistence Benchmark Results
       |==========================================
       |Iterations: ${result.iterationCount}
       |
       |Full format:  ${result.fullFormatMs} ms (${result.fullSizeKb}%.1f KB)
       |Cache format: ${result.cacheFormatMs} ms (${result.cacheSizeKb}%.1f KB)
       |
       |Ratio (cache/full): ${result.cacheVsFullRatio}%.3f
       |""".stripMargin

  def main(args: Array[String]): Unit =
    val iterations = args.headOption.flatMap(s => Try(s.toInt).toOption).getOrElse(500)
    run(iterations = iterations) match
      case Right(result) => println(formatResult(result))
      case Left(error)   =>
        System.err.println(error)
        sys.exit(1)

end UpdateReportPersistenceBenchmark
