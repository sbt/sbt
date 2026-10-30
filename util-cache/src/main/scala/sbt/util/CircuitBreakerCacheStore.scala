/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.util

import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal
import xsbti.{ HashedVirtualFileRef, VirtualFile }

/** How a cache store failure bears on whether calling that store again is worth it. */
enum CacheFailure:
  /** Calling again fails the same way, as a write does without permission to write. */
  case Permanent

  /** The store is unreachable or unhealthy, so a later call may still succeed. */
  case Transient

  /** The store answered, and a miss is no reason to stop calling it. */
  case Answered
end CacheFailure

/**
 * Stops calling `underlying` once it has failed, so that an unreachable or unauthorized cache
 * costs the build one failure instead of one per task. `get` and `put` block in the task body,
 * so without this every task that misses pays the full deadline on its own.
 */
final class CircuitBreakerCacheStore(
    val underlying: ActionCacheStore,
    classify: Throwable => CacheFailure,
    cooldown: FiniteDuration,
    logger: Logger,
    nanoTime: () => Long = () => System.nanoTime(),
) extends AbstractActionCacheStore,
      AutoCloseable:
  import CircuitBreakerCacheStore.*

  private val state = new AtomicReference(State())

  override def storeName: String = underlying.storeName

  override def get(request: GetActionResultRequest): Either[Throwable, ActionResult] =
    guardEither(Read)(underlying.get(request))

  override def put(request: UpdateActionResultRequest): Either[Throwable, ActionResult] =
    guardEither(Write)(underlying.put(request))

  override def findBlobs(refs: Seq[HashedVirtualFileRef]): Seq[HashedVirtualFileRef] =
    guard(Read, Nil)(underlying.findBlobs(refs))

  override def putBlobs(blobs: Seq[VirtualFile]): Seq[HashedVirtualFileRef] =
    guard(Write, Nil)(underlying.putBlobs(blobs))

  /**
   * Throws instead of returning no paths, because a caller reads an empty sync as a partial one
   * and would serve a hit without those outputs, whereas a throw degrades to running the task.
   */
  override def syncBlobs(refs: Seq[HashedVirtualFileRef], outputDirectory: Path): Seq[Path] =
    guard(Read, throw skipped)(underlying.syncBlobs(refs, outputDirectory))

  /** Forwarded so that wrapping a store does not take away its ability to release a connection. */
  override def close(): Unit = underlying match
    case c: AutoCloseable => c.close()
    case _                => ()

  override def toString: String = s"CircuitBreakerCacheStore($underlying)"

  /** The call is made outside the state updates, so no network wait happens under a retry loop. */
  private def guard[A](direction: Direction, denied: => A)(call: => A): A =
    if !allowed(direction) then denied
    else
      try
        val a = call
        record(None, direction)
        a
      catch
        case NonFatal(e) =>
          record(Some(e), direction)
          throw e

  private def guardEither(direction: Direction)(
      call: => Either[Throwable, ActionResult]
  ): Either[Throwable, ActionResult] =
    if !allowed(direction) then Left(skipped)
    else
      try
        val result = call
        record(result.left.toOption, direction)
        result
      catch
        case NonFatal(e) =>
          record(Some(e), direction)
          throw e

  @tailrec
  private def allowed(direction: Direction): Boolean =
    val current = state.get()
    val refused = direction match
      case Read  => current.readsRefused
      case Write => current.writesRefused
    if refused || current.probing then false
    else
      current.retryAt match
        case None     => true
        case Some(at) =>
          // Subtract rather than compare, so that a wrapped nanoTime still orders correctly.
          if nanoTime() - at < 0 then false
          // Whoever claims the probe is the one call that gets through; the rest are turned away.
          else if state.compareAndSet(current, current.copy(probing = true)) then true
          else allowed(direction)

  @tailrec
  private def record(failure: Option[Throwable], direction: Direction): Unit =
    val current = state.get()
    val (next, report) = transition(current, failure, direction)
    if state.compareAndSet(current, next) then report.foreach(logger.warn(_))
    else record(failure, direction)

  private def transition(
      current: State,
      failure: Option[Throwable],
      direction: Direction,
  ): (State, Option[String]) =
    val settled = current.copy(probing = false)
    failure.map(classify) match
      // Permission does not change within a session, and holds per direction: reads usually stay
      // allowed when a write is refused.
      case Some(CacheFailure.Permanent) =>
        val cleared = settled.copy(retryAt = None)
        direction match
          case Read if !current.readsRefused =>
            (cleared.copy(readsRefused = true), Some(refusal("a read", "reading from", failure)))
          case Write if !current.writesRefused =>
            (cleared.copy(writesRefused = true), Some(refusal("a write", "writing to", failure)))
          case _ => (cleared, None)
      case Some(CacheFailure.Transient) =>
        val report =
          if current.retryAt.nonEmpty then None
          else
            Some(
              s"$storeName is not answering (${reason(failure)}); " +
                s"not calling it again for $cooldown"
            )
        (settled.copy(retryAt = Some(nanoTime() + cooldown.toNanos)), report)
      // Reaching the store is what matters here, not whether it held the key.
      case _ => (settled.copy(retryAt = None), None)
    end match
  end transition

  private def refusal(what: String, doing: String, failure: Option[Throwable]): String =
    s"$storeName refused $what (${reason(failure)}); not $doing it again in this session"

  private def reason(failure: Option[Throwable]): String =
    failure.map(e => Option(e.getMessage).getOrElse(e.getClass.getName)).getOrElse("no reason")

  private def skipped: Throwable =
    RuntimeException(s"$storeName is not being called, because an earlier call failed")
end CircuitBreakerCacheStore

object CircuitBreakerCacheStore:
  private[util] enum Direction:
    case Read, Write
  export Direction.*

  /**
   * A refusal latches its direction until the build is reconfigured, while a store that is not
   * answering is retried once `retryAt` passes, by letting a single probing call through.
   */
  private case class State(
      readsRefused: Boolean = false,
      writesRefused: Boolean = false,
      retryAt: Option[Long] = None,
      probing: Boolean = false,
  )
end CircuitBreakerCacheStore
