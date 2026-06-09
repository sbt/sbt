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

  test("aggregates distinct files and monotonic bytes while resolving") {
    val p = new ResolutionProgress
    val log = new ResolutionProgressLogger(p)
    log.init(None)
    log.init(None) // two resolutions in flight
    log.downloadProgress("a.jar", 1000L)
    log.downloadProgress("a.jar", 4000L) // monotonic increase, total 4000
    log.downloadProgress("a.jar", 2000L) // out-of-order, must be ignored
    log.foundLocally("b.jar")
    log.downloadedArtifact("a.jar", success = true)
    log.downloadedArtifact("c.jar", success = false) // failed, must not count
    val line = p.snapshot().map(_._1)
    assert(line.isDefined, "expected a progress line while resolving")
    assert(line.exists(_.contains("2 files")), line.toString)
    log.stop()
    log.stop()
    assert(p.snapshot().isEmpty)
  }

  test("per-configuration logger sessions do not inflate the file count") {
    // coursier calls init/stop once per logger session — one per configuration resolution
    // (compile, runtime, test, ...), plus retries — not once per module, and it re-checks the
    // same urls in every session. Neither may inflate the count, and the line must not claim
    // a module count it cannot know.
    val p = new ResolutionProgress
    val log = new ResolutionProgressLogger(p)
    for (_ <- 1 to 3) { // e.g. compile, runtime, test sessions of one module's update
      log.init(None)
      log.foundLocally("x.pom")
      log.foundLocally("x.jar")
      log.stop()
    }
    log.init(None)
    log.foundLocally("x.jar") // re-checked again in the artifacts run
    val line = p.snapshot().map(_._1)
    assert(line.exists(_.contains("2 files")), line.toString) // x.pom + x.jar, once each
    assert(!line.exists(_.contains("module")), line.toString) // sessions are not modules
    log.stop()
  }

  test("checksum and signature companions are not counted as files") {
    val p = new ResolutionProgress
    val log = new ResolutionProgressLogger(p)
    log.init(None)
    log.foundLocally("a.jar")
    log.foundLocally("a.jar.sha1")
    log.downloadedArtifact("a.jar.md5", success = true)
    log.downloadedArtifact("a.pom.asc", success = true)
    val line = p.snapshot().map(_._1)
    assert(line.exists(_.contains("1 file,")), line.toString)
    log.stop()
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
    val line = p.snapshot().map(_._1)
    assert(line.exists(_.contains("2 files")), line.toString) // x + y, not reset
    log.stop()
  }

  test("snapshot exposes a non-negative burst elapsed for the renderer") {
    val p = new ResolutionProgress
    val log = new ResolutionProgressLogger(p)
    log.init(None)
    val snap = p.snapshot()
    assert(snap.exists(_._2 >= 0L), snap.toString)
    log.stop()
  }

end ResolutionProgressSpec
