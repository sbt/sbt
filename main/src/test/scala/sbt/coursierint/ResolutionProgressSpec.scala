/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.coursierint

import verify.BasicTestSuite

object ResolutionProgressSpec extends BasicTestSuite:

  test("a fresh per-command instance starts with no line") {
    val p = new ResolutionProgress
    assert(p.snapshot().isEmpty)
  }

  test("aggregates modules, artifacts, and monotonic bytes while resolving") {
    val p = new ResolutionProgress
    val log = new ResolutionProgressLogger(p)
    log.init(None)
    log.init(None) // two resolutions in flight
    log.downloadProgress("a.jar", 1000L)
    log.downloadProgress("a.jar", 4000L) // monotonic increase, total 4000
    log.downloadProgress("a.jar", 2000L) // out-of-order, must be ignored
    log.foundLocally("b.jar") // counts as an artifact
    log.downloadedArtifact("a.jar", success = true) // counts
    log.downloadedArtifact("c.jar", success = false) // failed, must not count
    val line = p.snapshot()
    assert(line.isDefined, "expected a progress line while resolving")
    assert(line.exists(_.contains("2 modules")), line.toString)
    assert(line.exists(_.contains("2 artifacts")), line.toString)
    log.stop()
    log.stop()
    assert(p.snapshot().isEmpty)
  }

  test("counts persist across the resolve and artifacts phases of one command") {
    val p = new ResolutionProgress
    val log = new ResolutionProgressLogger(p)
    // resolve phase
    log.init(None)
    log.foundLocally("x.jar")
    log.stop()
    assert(p.snapshot().isEmpty) // idle between phases
    // artifacts phase of the SAME command: counts accumulate, they do not reset
    log.init(None)
    log.downloadedArtifact("y.jar", success = true)
    val line = p.snapshot()
    assert(
      line.exists(_.contains("2 modules")),
      line.toString
    ) // resolve + artifacts, not reset to 1
    assert(line.exists(_.contains("2 artifacts")), line.toString) // x + y, not reset
    log.stop()
  }

end ResolutionProgressSpec
