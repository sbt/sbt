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
 * download-pool threads and read by `TaskProgress` to render a single super-shell line.
 *
 * The counting model matches how coursier actually drives a `CacheLogger`:
 *   - `init`/`stop` arrive once per logger session — one per configuration resolution and one per
 *     artifacts run, plus network retries — NOT once per module, so no module count is reported;
 *     `inFlight` only controls when the line is visible.
 *   - `foundLocally`/`downloadedArtifact` fire on every cache check, repeating the same url across
 *     sessions and including checksum companions, so files are counted as distinct urls with
 *     checksum/signature companions excluded.
 *   - `downloadProgress` carries a cumulative per-url byte count; byte accounting takes a monotonic
 *     per-url delta and so can never double-count or go backwards.
 *
 * There is no cross-command reset: the instance is born empty and discarded with the command.
 */
private[sbt] final class ResolutionProgress {
  private val inFlight = new AtomicInteger(0)
  private val burstStartNanos = new AtomicLong(System.nanoTime())
  private val files = ConcurrentHashMap.newKeySet[String]
  private val bytes = new AtomicLong(0L)
  private val seen = new ConcurrentHashMap[String, java.lang.Long]

  def onInit(): Unit = {
    val now = System.nanoTime()
    // 0 -> 1 starts a new burst (e.g. the artifacts phase after an idle gap): restart the clock.
    if (inFlight.getAndIncrement() == 0) burstStartNanos.set(now)
    ()
  }

  def onStop(): Unit = {
    inFlight.updateAndGet(n => math.max(0, n - 1))
    ()
  }

  def onFile(url: String): Unit = {
    if (!ResolutionProgress.isChecksumLike(url)) files.add(url)
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

  /**
   * While at least one resolution is in flight: the render line plus the elapsed micros of the
   * current burst (the super shell appends elapsed to every item, so this renders as a live
   * counter). Else None (the line disappears).
   */
  def snapshot(): Option[(String, Long)] =
    if (inFlight.get() <= 0) None
    else {
      val n = files.size()
      val mib = bytes.get().toDouble / (1024.0 * 1024.0)
      val label = if (n == 1) "file" else "files"
      val elapsedMicros = math.max(0L, (System.nanoTime() - burstStartNanos.get()) / 1000L)
      Some((f"downloading $n $label, $mib%.1f MiB", elapsedMicros))
    }
}

private[sbt] object ResolutionProgress {
  // Checksum/signature companions go through the same cache callbacks as real files; counting
  // them would roughly double the file count.
  private val checksumLikeSuffixes = Seq(".sha1", ".sha256", ".sha512", ".md5", ".asc", ".sig")
  private[coursierint] def isChecksumLike(url: String): Boolean = {
    val lower = url.toLowerCase(java.util.Locale.ROOT)
    checksumLikeSuffixes.exists(s => lower.endsWith(s))
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
  override def foundLocally(url: String): Unit = sink.onFile(url)
  override def downloadedArtifact(url: String, success: Boolean): Unit =
    if (success) sink.onFile(url)
  override def downloadProgress(url: String, downloaded: Long): Unit =
    sink.onProgress(url, downloaded)
}
