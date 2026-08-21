/*
 * sbt
 * Copyright 2026, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import sbt.protocol.testing.TestResult
import verify.BasicTestSuite

object SuiteResultSpec extends BasicTestSuite:

  private def emptySuite(result: TestResult): SuiteResult =
    new SuiteResult(result, 0, 0, 0, 0, 0, 0, 0)

  private def assertCombined(
      left: TestResult,
      right: TestResult
  )(expected: TestResult): Unit =
    val actual = (emptySuite(left) + emptySuite(right)).result
    assert(actual == expected)

  test("SuiteResult combines Passed and Passed as Passed") {
    assertCombined(TestResult.Passed, TestResult.Passed)(TestResult.Passed)
  }

  test("SuiteResult combines Passed and Empty as Passed") {
    assertCombined(TestResult.Passed, TestResult.Empty)(TestResult.Passed)
    assertCombined(TestResult.Empty, TestResult.Passed)(TestResult.Passed)
  }

  test("SuiteResult combines Passed and Failed as Failed") {
    assertCombined(TestResult.Passed, TestResult.Failed)(TestResult.Failed)
    assertCombined(TestResult.Failed, TestResult.Passed)(TestResult.Failed)
  }

  test("SuiteResult combines Passed and Error as Error") {
    assertCombined(TestResult.Passed, TestResult.Error)(TestResult.Error)
    assertCombined(TestResult.Error, TestResult.Passed)(TestResult.Error)
  }

  test("SuiteResult combines Empty and Empty as Empty") {
    assertCombined(TestResult.Empty, TestResult.Empty)(TestResult.Empty)
  }

  test("SuiteResult combines Empty and Failed as Failed") {
    assertCombined(TestResult.Empty, TestResult.Failed)(TestResult.Failed)
    assertCombined(TestResult.Failed, TestResult.Empty)(TestResult.Failed)
  }

  test("SuiteResult combines Empty and Error as Error") {
    assertCombined(TestResult.Empty, TestResult.Error)(TestResult.Error)
    assertCombined(TestResult.Error, TestResult.Empty)(TestResult.Error)
  }

  test("SuiteResult combines Failed and Failed as Failed") {
    assertCombined(TestResult.Failed, TestResult.Failed)(TestResult.Failed)
  }

  test("SuiteResult combines Failed and Error as Error") {
    assertCombined(TestResult.Failed, TestResult.Error)(TestResult.Error)
    assertCombined(TestResult.Error, TestResult.Failed)(TestResult.Error)
  }

  test("SuiteResult combines Error and Error as Error") {
    assertCombined(TestResult.Error, TestResult.Error)(TestResult.Error)
  }

end SuiteResultSpec
