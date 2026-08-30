/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import sbt.protocol.testing.TestResult

object TestSummaryTest extends verify.BasicTestSuite:

  private def output(suites: (String, SuiteResult)*): Tests.Output =
    Tests.Output(TestResult.Passed, suites.toMap, Iterable.empty)

  test("SuiteResult.withoutThrowables returns the original result when it has no throwables") {
    val source = new SuiteResult(TestResult.Failed, 1, 2, 3, 4, 5, 6, 7)
    assert(source.withoutThrowables eq source)
  }

  test("SuiteResult.withoutThrowables drops throwables and preserves result data") {
    val thrown = new AssertionError("boom")
    val source =
      new SuiteResult(TestResult.Failed, 1, 2, 3, 4, 5, 6, 7, thrown :: Nil)
    val sanitized = source.withoutThrowables
    assert(!(sanitized eq source))
    assert(sanitized.throwables.isEmpty)
    assert(sanitized.result == source.result)
    assert(sanitized.passedCount == source.passedCount)
    assert(sanitized.failureCount == source.failureCount)
    assert(sanitized.errorCount == source.errorCount)
    assert(sanitized.skippedCount == source.skippedCount)
    assert(sanitized.ignoredCount == source.ignoredCount)
    assert(sanitized.canceledCount == source.canceledCount)
    assert(sanitized.pendingCount == source.pendingCount)
  }

  test("Tests.Output.withoutThrowables sanitizes every suite and preserves output data") {
    val thrown = new AssertionError("boom")
    val withoutThrowables = new SuiteResult(TestResult.Passed, 1, 0, 0, 0, 0, 0, 0)
    val withThrowables =
      new SuiteResult(TestResult.Failed, 0, 1, 0, 0, 0, 0, 0, thrown :: Nil)
    val summaries = Iterable(Tests.Summary("framework", "summary"))
    val source = Tests.Output(
      TestResult.Failed,
      Map("passed" -> withoutThrowables, "failed" -> withThrowables),
      summaries
    )
    val sanitized = source.withoutThrowables
    assert(!(sanitized eq source))
    assert(sanitized.overall == source.overall)
    assert(sanitized.summaries == summaries)
    assert(sanitized.events("passed") eq withoutThrowables)
    assert(sanitized.events("failed").throwables.isEmpty)
    assert(sanitized.events("failed").result == withThrowables.result)
    assert(sanitized.events("failed").failureCount == withThrowables.failureCount)
  }

  test("append strips SuiteResult.throwables so lingering entries cannot pin the classloader") {
    val thrown = new AssertionError("boom")
    val withThrowables =
      new SuiteResult(TestResult.Passed, 1, 0, 0, 0, 0, 0, 0, thrown :: Nil)
    TestSummary.clear()
    TestSummary.append("a / Test / test", output("A" -> withThrowables), Vector.empty, Vector.empty)
    val drained = TestSummary.drain()
    assert(drained.size == 1)
    assert(drained.head.testOutput.events("A").throwables.isEmpty)
    assert(TestSummary.drain().isEmpty)
  }

end TestSummaryTest
