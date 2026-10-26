package sbt
package internal
package communitybuild

import java.nio.file.{ Files, Paths, StandardOpenOption }

final case class ParsedCacheSummary(
    success: Boolean,
    elapsedSeconds: Long,
    hitRate: Option[Double],
    remoteCacheHits: Int,
    diskCacheHits: Int,
    onsiteTasks: Int,
    errors: Int,
    tests: Option[ParsedTestSummary] = None,
)

final case class ParsedTestSummary(
    total: Int,
    failed: Int,
    errors: Int,
    passed: Int,
    cached: Int,
):
  /** Fraction of tests served from the cache, where 1.0 is 100%; 0.0 when there are no tests. */
  def testHitRate: Double =
    if total == 0 then 0.0 else cached.toDouble / total

object ParsedTestSummary:
  private val Pattern =
    """(?:passed|failed): total (\d+), failed (\d+), errors (\d+), passed (\d+)(?:, cached (\d+))?""".r.unanchored

  def parse(line: String): Option[ParsedTestSummary] = line match
    case Pattern(total, failed, errors, passed, cached) =>
      Some(
        ParsedTestSummary(
          total = total.toInt,
          failed = failed.toInt,
          errors = errors.toInt,
          passed = passed.toInt,
          cached = Option(cached).fold(0)(_.toInt),
        )
      )
    case _ => None
end ParsedTestSummary

/**
 * Collects cache summaries, attaching the preceding test summary to each.
 * Summaries without a cache hit rate (sbt 1.x) are used only when there are none with it,
 * since sbt 2.x also prints a plain total after each cache summary.
 */
final class CacheSummaryCollector:
  private var pendingTests: Option[ParsedTestSummary] = None
  private val withCache = List.newBuilder[ParsedCacheSummary]
  private val withoutCache = List.newBuilder[ParsedCacheSummary]

  def add(line: String): Unit =
    ParsedTestSummary.parse(line) match
      case Some(tests) => pendingTests = Some(tests)
      case None        =>
        ParsedCacheSummary
          .parse(line)
          .foreach(summary =>
            val target = if summary.hitRate.isDefined then withCache else withoutCache
            target += summary.copy(tests = pendingTests)
            pendingTests = None
          )

  def result(): List[ParsedCacheSummary] =
    val cached = withCache.result()
    if cached.nonEmpty then cached else withoutCache.result()
end CacheSummaryCollector

/** Describes one sbt invocation of a community project. */
final case class RunInfo(
    project: String,
    run: String,
    sbtVersion: String,
    wallClockSeconds: Double,
)

object ParsedCacheSummary:
  private val Elapsed = """elapsed time: (\d+) s""".r.unanchored
  private val HitRate = """\bcache (\d+)%""".r.unanchored
  private val RemoteHits = """(\d+) remote cache hits?\b""".r.unanchored
  private val DiskHits = """(\d+) disk cache hits?\b""".r.unanchored
  private val Onsite = """(\d+) onsite tasks?\b""".r.unanchored
  private val Errors = """(\d+) errors?\b""".r.unanchored

  def parse(line: String): Option[ParsedCacheSummary] =
    first(Elapsed, line).map(elapsed =>
      ParsedCacheSummary(
        success = !line.contains("[error]"),
        elapsedSeconds = elapsed.toLong,
        hitRate = first(HitRate, line).map(_.toInt / 100.0),
        remoteCacheHits = count(RemoteHits, line),
        diskCacheHits = count(DiskHits, line),
        onsiteTasks = count(Onsite, line),
        errors = count(Errors, line),
      )
    )

  private def first(regex: scala.util.matching.Regex, line: String): Option[String] =
    regex.findFirstMatchIn(line).map(_.group(1))

  private def count(regex: scala.util.matching.Regex, line: String): Int =
    first(regex, line).fold(0)(_.toInt)

  private[communitybuild] val tableHeader =
    """|| Project | Run | sbt | Status | Wall clock | Elapsed | Cache | Remote hits | Disk hits | Onsite tasks | Errors | Tests | Cached tests |
      ||---|---|---|---|---|---|---|---|---|---|---|---|---|
      |""".stripMargin

  def markdownRow(info: RunInfo, summary: ParsedCacheSummary): String =
    import summary.*
    val status = if success then "success" else "error"
    val wallClock = f"${info.wallClockSeconds}%.1fs"
    val percent = hitRate.fold("")(rate => f"${rate * 100}%.0f%%")
    val testCount = tests.fold("")(_.total.toString)
    val cachedTests = tests.fold("")(_.cached.toString)
    s"| `${info.project}` | ${info.run} | ${info.sbtVersion} | $status | $wallClock | ${elapsedSeconds}s | $percent | $remoteCacheHits | $diskCacheHits | $onsiteTasks | $errors | $testCount | $cachedTests |\n"

  def report(info: RunInfo, summaries: Seq[ParsedCacheSummary]): Unit =
    summaries.foreach(s => log(s"[${info.project}] ${info.run}: $s"))
    sys.env
      .get("GITHUB_STEP_SUMMARY")
      .filter(_ => summaries.nonEmpty)
      .foreach(p =>
        val path = Paths.get(p)
        val hasHeader = Files.exists(path) && Files.readString(path).contains(tableHeader)
        val rows = summaries.map(markdownRow(info, _)).mkString
        Files.writeString(
          path,
          (if hasHeader then "" else tableHeader) + rows,
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND,
        )
      )
end ParsedCacheSummary
