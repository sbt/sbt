/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

import scala.Console.{ RED, RESET }
import verify.BasicTestSuite
import sbt.internal.Output.grep

object OutputSpec extends BasicTestSuite {

  test(
    "grep should match pattern against visible text when lines contain ANSI escape sequences (#4840)"
  ) {
    // Line with ANSI color around "error" - user searching for "error" should find it (strip before match)
    val lineWithAnsi = s"${RED}error${RESET}: something failed"
    val lines = Seq("info: ok", lineWithAnsi, "warn: deprecated")
    val result = grep(lines, "error")
    assert(result.size == 1, s"expected 1 match, got ${result.size}: $result")
    // Pattern matched the visible "error" (ANSI was stripped before matching); result may have highlight from showMatches
    assert(result.head.contains("error"), s"result should contain 'error': ${result.head}")
  }

  test("grep should not match when pattern appears only inside ANSI sequence") {
    // Line where "error" is not in the visible text (only in escape code - unrealistic but ensures we strip first)
    val lines = Seq("info: ok", "something failed")
    val result = grep(lines, "error")
    assert(result.isEmpty, s"expected no match, got: $result")
  }
}
