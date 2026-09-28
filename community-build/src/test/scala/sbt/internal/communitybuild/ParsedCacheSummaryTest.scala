package sbt
package internal
package communitybuild

import org.junit.Test
import org.junit.experimental.categories.Category

@Category(Array(classOf[TestCategory]))
class ParsedCacheSummaryTest:
  @Test def parseRemoteAndDiskHits(): Unit =
    val summary = ParsedCacheSummary.parse(
      "[success] elapsed time: 4 s, cache 100%, 191 remote cache hits, 8 disk cache hits"
    )
    assert(summary == Some(ParsedCacheSummary(true, 4, 1.0, 191, 8, 0, 0)))

  @Test def parseSingularCountsAndDuration(): Unit =
    val summary = ParsedCacheSummary.parse(
      "[success] elapsed time: 74 s (0:01:14.0), cache 58%, 1 disk cache hit, 13 onsite tasks"
    )
    assert(summary == Some(ParsedCacheSummary(true, 74, 0.58, 0, 1, 13, 0)))

  @Test def parseErrors(): Unit =
    val summary = ParsedCacheSummary.parse(
      "[error] elapsed time: 9 s, cache 91%, 1624 disk cache hits, 157 onsite tasks, 35 errors"
    )
    assert(summary == Some(ParsedCacheSummary(false, 9, 0.91, 0, 1624, 157, 35)))

  @Test def ignoreLinesWithoutCache(): Unit =
    assert(ParsedCacheSummary.parse("[success] elapsed time: 18 s").isEmpty)
    assert(ParsedCacheSummary.parse("[info] compiling 3 Scala sources").isEmpty)

  @Test def markdownRow(): Unit =
    val row = ParsedCacheSummary.markdownRow("scalaz", ParsedCacheSummary(true, 4, 1.0, 191, 8, 0, 0))
    assert(row == "| `scalaz` | success | 4s | 100% | 191 | 8 | 0 | 0 |  |  |\n")

  @Test def tableHeaderLines(): Unit =
    val lines = ParsedCacheSummary.tableHeader.linesIterator.toList
    assert(lines.size == 2)
    assert(lines.forall(line => line.startsWith("|") && line.endsWith("|")))

  @Test def parseTestSummary(): Unit =
    val tests = ParsedTestSummary.parse("[info] passed: total 94, failed 0, errors 0, passed 94, cached 94")
    assert(tests == Some(ParsedTestSummary(94, 0, 0, 94, 94)))

  @Test def parseFailedTestSummary(): Unit =
    val tests = ParsedTestSummary.parse("[error] failed: total 1, failed 1, errors 0, passed 0, cached 0")
    assert(tests == Some(ParsedTestSummary(1, 1, 0, 0, 0)))

  @Test def collectorAttachesPrecedingTestSummary(): Unit =
    val collector = CacheSummaryCollector()
    List(
      "[info] passed: total 94, failed 0, errors 0, passed 94, cached 94",
      "[success] elapsed time: 5 s, cache 93%, 126 remote cache hits, 21 disk cache hits, 10 onsite tasks",
      "[success] elapsed time: 1 s, cache 100%, 3 disk cache hits",
    ).foreach(collector.add)
    val tests = Some(ParsedTestSummary(94, 0, 0, 94, 94))
    assert(
      collector.result() == List(
        ParsedCacheSummary(true, 5, 0.93, 126, 21, 10, 0, tests),
        ParsedCacheSummary(true, 1, 1.0, 0, 3, 0, 0, None),
      )
    )

  @Test def markdownRowWithTests(): Unit =
    val summary = ParsedCacheSummary(true, 5, 0.93, 126, 21, 10, 0, Some(ParsedTestSummary(94, 0, 0, 94, 94)))
    val row = ParsedCacheSummary.markdownRow("chimney", summary)
    assert(row == "| `chimney` | success | 5s | 93% | 126 | 21 | 10 | 0 | 94 | 94 |\n")

  @Test def testHitRate(): Unit =
    assert(ParsedTestSummary(94, 0, 0, 94, 94).testHitRate == 1.0)
    assert(ParsedTestSummary(4, 0, 0, 4, 1).testHitRate == 0.25)
    assert(ParsedTestSummary(0, 0, 0, 0, 0).testHitRate == 0.0)
end ParsedCacheSummaryTest
