/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.util

import java.nio.file.Path
import scala.collection.mutable
import scala.concurrent.duration.*
import verify.BasicTestSuite
import xsbti.{ HashedVirtualFileRef, VirtualFile }

object CircuitBreakerCacheStoreTest extends BasicTestSuite:
  private val cooldown = 60.seconds

  test("A store that is not answering is called once, then left alone until the cooldown is up"):
    val store = StubStore(CacheFailure.Transient)
    val clock = Clock()
    val breaker = newBreaker(store, clock)

    assert(breaker.get(request).isLeft)
    assert(store.calls == 1)

    // Every later task would otherwise pay the same deadline again.
    assert(breaker.get(request).isLeft)
    assert(breaker.findBlobs(Nil).isEmpty)
    assert(store.calls == 1)

    clock.advance(cooldown - 1.second)
    assert(breaker.get(request).isLeft)
    assert(store.calls == 1)

    clock.advance(2.seconds)
    assert(breaker.get(request).isLeft)
    assert(store.calls == 2, "expected one call through once the cooldown was up")

    // Still failing, so it closes again rather than probing on every call.
    assert(breaker.get(request).isLeft)
    assert(store.calls == 2)

  test("A probe that gets through puts the store back in use"):
    val store = StubStore(CacheFailure.Transient)
    val clock = Clock()
    val breaker = newBreaker(store, clock)

    assert(breaker.get(request).isLeft)
    clock.advance(cooldown)
    store.outcome = None
    assert(breaker.get(request).isRight)
    assert(breaker.get(request).isRight)
    assert(store.calls == 3)

  test("A refused write is not retried, while reads keep working"):
    val store = StubStore(CacheFailure.Permanent, writesOnly = true)
    val breaker = newBreaker(store, Clock())

    assert(breaker.put(putRequest).isLeft)
    assert(store.calls == 1)

    // Permission does not change within a session, so there is nothing to retry.
    assert(breaker.put(putRequest).isLeft)
    assert(breaker.putBlobs(Nil).isEmpty)
    assert(store.calls == 1)

    // Read permission is separate and usually still there.
    assert(breaker.get(request).isRight)
    assert(store.calls == 2)

  test("A miss is not a reason to stop calling the store"):
    val store = StubStore(CacheFailure.Answered)
    val breaker = newBreaker(store, Clock())

    assert(breaker.get(request).isLeft)
    assert(breaker.get(request).isLeft)
    assert(breaker.get(request).isLeft)
    assert(store.calls == 3)

  test("A store that is not answering is reported once, not once per task"):
    val store = StubStore(CacheFailure.Transient)
    val clock = Clock()
    val messages = mutable.ListBuffer.empty[String]
    val breaker = newBreaker(store, clock, messages)

    breaker.get(request)
    breaker.get(request)
    clock.advance(cooldown)
    breaker.get(request)

    assert(messages.size == 1, messages.mkString("; "))
    assert(messages.head.contains("not calling it again"), messages.head)

  /**
   * An empty sync reads as a partial one to `AggregateActionCacheStore`, which would then serve a
   * hit without those outputs, so the breaker has to throw for the caller to run the task instead.
   */
  test("A skipped sync throws rather than reporting no paths"):
    val store = StubStore(CacheFailure.Transient)
    val breaker = newBreaker(store, Clock())

    assert(breaker.get(request).isLeft)
    val thrown =
      try
        breaker.syncBlobs(Nil, Path.of("."))
        false
      catch case _: RuntimeException => true
    assert(thrown)

  // ---------- helpers ----------

  private def request = GetActionResultRequest(Digest.zero, false, false, Vector.empty)
  private def putRequest = UpdateActionResultRequest(Digest.zero, Vector.empty, exitCode = 0)

  private final class Clock:
    private var nanos = 0L
    def advance(by: FiniteDuration): Unit = nanos += by.toNanos
    def read(): Long = nanos

  private def newBreaker(
      store: ActionCacheStore,
      clock: Clock,
      messages: mutable.ListBuffer[String] = mutable.ListBuffer.empty,
  ): CircuitBreakerCacheStore =
    val logger = new Logger:
      override def trace(t: => Throwable): Unit = ()
      override def success(message: => String): Unit = ()
      override def log(level: Level.Value, message: => String): Unit = messages += message
    CircuitBreakerCacheStore(store, classify, cooldown, logger, () => clock.read())

  private def classify(e: Throwable): CacheFailure = e match
    case Failed(kind) => kind
    case _            => CacheFailure.Answered

  private case class Failed(kind: CacheFailure) extends RuntimeException(s"simulated $kind")

  private final class StubStore(kind: CacheFailure, writesOnly: Boolean = false)
      extends AbstractActionCacheStore:
    var calls = 0
    var outcome: Option[CacheFailure] = Some(kind)

    override def storeName: String = "stub"

    private def failure(isWrite: Boolean): Option[Throwable] =
      calls += 1
      if writesOnly && !isWrite then None else outcome.map(Failed(_))

    override def get(request: GetActionResultRequest): Either[Throwable, ActionResult] =
      failure(isWrite = false) match
        case Some(e) => Left(e)
        case None    => Right(ActionResult(Vector.empty, storeName))

    override def put(request: UpdateActionResultRequest): Either[Throwable, ActionResult] =
      failure(isWrite = true) match
        case Some(e) => Left(e)
        case None    => Right(ActionResult(Vector.empty, storeName))

    override def findBlobs(refs: Seq[HashedVirtualFileRef]): Seq[HashedVirtualFileRef] =
      failure(isWrite = false).foreach(throw _)
      Nil

    override def putBlobs(blobs: Seq[VirtualFile]): Seq[HashedVirtualFileRef] =
      failure(isWrite = true).foreach(throw _)
      Nil

    override def syncBlobs(refs: Seq[HashedVirtualFileRef], outputDirectory: Path): Seq[Path] =
      failure(isWrite = false).foreach(throw _)
      Nil
  end StubStore
end CircuitBreakerCacheStoreTest
