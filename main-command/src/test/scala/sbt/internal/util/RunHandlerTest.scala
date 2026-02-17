/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import verify.BasicTestSuite

object RunHandlerTest extends BasicTestSuite:
  test("mergedEnvVars includes current process environment and applies explicit overrides"):
    val overrides = Map("PATH" -> "__override__", "SBT_RUNUTIL_TEST_VAR" -> "value")
    val result = RunHandler.mergedEnvVars(overrides)

    assert(result.get("SBT_RUNUTIL_TEST_VAR").contains("value"))
    assert(result.get("PATH").contains("__override__"))
    assert(result.contains("HOME"))

end RunHandlerTest
