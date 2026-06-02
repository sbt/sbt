/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package coursierint

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.{ AtomicInteger, AtomicLong }
import lmcoursier.definitions.CacheLogger

/**
 * Per-command running total of dependency-resolution progress.
 *
 * One instance is created per command in `MainLoop.next` (held under `Keys.resolutionProgress`, the
 * same lifecycle as `Keys.taskProgress`). It is fed by [[ResolutionProgressLogger]] from coursier's
 * download-pool threads and read by `TaskProgress` to render a single super-shell line. Because
 * those callbacks run in parallel across modules, every field is atomic and byte accounting uses a
 * monotonic per-url delta and so can never go backwards. There is no cross-command reset: the
 * instance is born empty and discarded with the command.
 */
private[sbt] final class ResolutionProgress {
  private val inFlight = new AtomicInteger(0)
  private val modules = new AtomicInteger(0)
  private val artifacts = new AtomicLong(0L)
  private val bytes = new AtomicLong(0L)
  private val seen = new ConcurrentHashMap[String, java.lang.Long]

  def onInit(): Unit = {
    inFlight.incrementAndGet()
    modules.incrementAndGet()
    ()
  }

  def onStop(): Unit = {
    inFlight.updateAndGet(n => math.max(0, n - 1))
    ()
  }

  def onArtifact(): Unit = {
    artifacts.incrementAndGet()
    ()
  }

  def onProgress(url: String, downloaded: Long): Unit = {
    // compute keeps the read-compare-add atomic per url, so concurrent progress callbacks for the
    // same url can neither double-count nor lose a byte delta; `seen` always holds the max seen.
    seen.compute(
      url,
      (_: String, prev: java.lang.Long) => {
        val p: Long = if (prev == null) 0L else prev.longValue
        if (downloaded > p) bytes.addAndGet(downloaded - p)
        java.lang.Long.valueOf(math.max(downloaded, p))
      }
    )
    ()
  }

  /** A render string while at least one resolution is in flight, else None (the line disappears). */
  def snapshot(): Option[String] =
    if (inFlight.get() <= 0) None
    else {
      val m = modules.get()
      val a = artifacts.get()
      val mib = bytes.get().toDouble / (1024.0 * 1024.0)
      val mLabel = if (m == 1) "module" else "modules"
      val aLabel = if (a == 1) "artifact" else "artifacts"
      Some(f"Updating $m $mLabel, $a $aLabel, $mib%.1f MiB")
    }
}

/**
 * A coursier `CacheLogger` that feeds a per-command [[ResolutionProgress]]. Supplying any logger to
 * lm-coursier suppresses coursier's own per-module progress bar and lets resolution run in parallel
 * across modules; the aggregate is rendered at the sbt task level instead.
 */
private[sbt] final class ResolutionProgressLogger(sink: ResolutionProgress) extends CacheLogger {
  override def init(sizeHint: Option[Int]): Unit = sink.onInit()
  override def stop(): Unit = sink.onStop()
  override def foundLocally(url: String): Unit = sink.onArtifact()
  override def downloadedArtifact(url: String, success: Boolean): Unit =
    if (success) sink.onArtifact()
  override def downloadProgress(url: String, downloaded: Long): Unit =
    sink.onProgress(url, downloaded)
}
