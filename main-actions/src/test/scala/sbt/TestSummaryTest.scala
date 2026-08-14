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
