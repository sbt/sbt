/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.std

import java.util.concurrent.{ ConcurrentLinkedQueue, CountDownLatch, Executors, TimeUnit }
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicInteger, AtomicReference }
import hedgehog.Result
import hedgehog.runner.*
import sbt.{
  ConcurrentRestrictions,
  Execute,
  ExecuteProgress,
  Result as TaskResult,
  Task,
  TaskId,
  Triggers
}
import sbt.internal.util.RMap
import sbt.std.TaskExtra.*
import scala.jdk.CollectionConverters.*

object ExecuteAbortTest extends Properties:
  override def tests: List[Test] = List(
    example("an aborted evaluation reports all of its tasks", abortedTasks),
    example("an aborted evaluation reports no work of still running tasks", lateWork),
    example("an abort keeps the original failure when progress fails", failedProgress),
    example("an abort keeps the original failure when progress rethrows it", rethrownFailure),
    example(
      "an abort keeps the original failure when progress is interrupted",
      interruptedProgress
    ),
    example("an abort reaches every reporter when one fails", failingReporter),
    example("an abort waits for work reports in progress", reportInProgress),
    example("an abort does not wait indefinitely for a work report", blockedReport),
    example("an interrupted abort still waits for work reports in progress", interruptedWait),
  )

  private class Recorder extends ExecuteProgress:
    val registered = new ConcurrentLinkedQueue[TaskId[?]]
    val aborted = new ConcurrentLinkedQueue[Vector[TaskId[?]]]
    val late = new ConcurrentLinkedQueue[String]
    val completions = new AtomicInteger
    private def record(callback: String): Unit = if !aborted.isEmpty then late.add(callback)
    override def initial(): Unit = ()
    override def afterRegistered(
        task: TaskId[?],
        allDeps: Iterable[TaskId[?]],
        pendingDeps: Iterable[TaskId[?]]
    ): Unit =
      record(s"afterRegistered $task")
      registered.add(task)
    override def afterReady(task: TaskId[?]): Unit = record(s"afterReady $task")
    override def beforeWork(task: TaskId[?]): Unit = record(s"beforeWork $task")
    override def afterWork[A](task: TaskId[A], result: Either[TaskId[A], TaskResult[A]]): Unit =
      record(s"afterWork $task")
    override def afterCompleted[A](task: TaskId[A], result: TaskResult[A]): Unit =
      record(s"afterCompleted $task")
    override def afterAllCompleted(results: RMap[TaskId, TaskResult]): Unit =
      completions.incrementAndGet()
      ()
    override def afterAborted(tasks: Iterable[TaskId[?]]): Unit =
      aborted.add(tasks.toVector)
      ()
    override def stop(): Unit = ()
  end Recorder

  private final case class Evaluation(
      result: TaskResult[?],
      recorder: Recorder,
      lateRan: Boolean,
      idle: Boolean
  )

  private val sentinel = ConcurrentRestrictions.Tag("sentinel")
  private val afterRunning = ConcurrentRestrictions.Tag("after-running")

  private def evaluate(recorder: Recorder): Evaluation =
    val pool = Executors.newCachedThreadPool()
    val waitingStarted, runningStarted, release, lateRan = new CountDownLatch(1)
    val runningDone = new AtomicBoolean
    val service = ConcurrentRestrictions.completionService(
      pool,
      ConcurrentRestrictions.tagged(tags =>
        tags.getOrElse(afterRunning, 0) == 0 || runningDone.get
      ),
      _ => (),
      _.tags.contains(sentinel)
    )
    val waiting = task:
      waitingStarted.countDown()
      new CountDownLatch(1).await()
    val trigger = task:
      waitingStarted.await()
      runningStarted.await()
      service.cancelSentinels()
    val running = task:
      runningStarted.countDown()
      release.await()
      runningDone.set(true)
    val late = task(lateRan.countDown()).tag(afterRunning)
    val root = Seq(waiting.tag(sentinel), trigger, running, late).join
    val x = new Execute(Execute.config(false), Execute.noTriggers, recorder)(using
      Transform(Transform.DummyTaskMap(Nil))
    )
    try
      val result = x.run(root)(using service)
      release.countDown()
      val ran = lateRan.await(10, TimeUnit.SECONDS)
      pool.shutdown()
      Evaluation(result, recorder, ran, pool.awaitTermination(10, TimeUnit.SECONDS))
    finally
      release.countDown()
      pool.shutdownNow()
      ()
  end evaluate

  private final case class Abort(
      outcome: Either[Throwable, TaskResult[?]],
      interrupted: Boolean,
      millis: Long
  )

  private def abort(
      progress: ExecuteProgress,
      tasks: Seq[Task[?]] = Nil,
      before: CountDownLatch = new CountDownLatch(0)
  ): Abort =
    val pool = Executors.newCachedThreadPool()
    val waitingStarted = new CountDownLatch(1)
    val service = ConcurrentRestrictions.completionService(
      pool,
      ConcurrentRestrictions.unrestricted,
      _ => (),
      _.tags.contains(sentinel)
    )
    val waiting = task:
      waitingStarted.countDown()
      new CountDownLatch(1).await()
    val trigger = task:
      waitingStarted.await()
      before.await()
      service.cancelSentinels()
    val root = (Seq(waiting.tag(sentinel), trigger) ++ tasks).join
    val x = new Execute(Execute.config(false), Execute.noTriggers, progress)(using
      Transform(Transform.DummyTaskMap(Nil))
    )
    val start = System.nanoTime
    try
      val outcome =
        try Right(x.run(root)(using service))
        catch case e: Throwable => Left(e)
      Abort(outcome, Thread.interrupted(), (System.nanoTime - start) / 1000000)
    finally
      pool.shutdownNow()
      ()
  end abort

  private def suppressed(abort: Abort, failure: Throwable): Boolean = abort.outcome match
    case Right(result @ TaskResult.Inc(i)) => cancelled(result) && i.getSuppressed.contains(failure)
    case _                                 => false

  private def cancelled(result: TaskResult[?]): Boolean = result match
    case TaskResult.Inc(i) => i.message.contains("cancelled")
    case _                 => false

  private def abortedTasks: Result =
    val e = evaluate(new Recorder)
    val reported = e.recorder.aborted.asScala.toVector
    val registered = e.recorder.registered.asScala.toSet
    Result
      .assert(
        cancelled(e.result) && reported.size == 1 && reported.head.toSet == registered &&
          reported.head.size == registered.size && registered.size > 4 &&
          e.recorder.completions.get == 0
      )
      .log(
        s"result=${e.result} reported=$reported registered=$registered " +
          s"completions=${e.recorder.completions.get}"
      )

  private def lateWork: Result =
    val e = evaluate(new Recorder)
    val late = e.recorder.late.asScala.toVector
    Result
      .assert(cancelled(e.result) && e.lateRan && e.idle && late.isEmpty)
      .log(s"result=${e.result} lateRan=${e.lateRan} idle=${e.idle} late=$late")

  private def failedProgress: Result =
    val failure = new IllegalStateException("progress failure")
    val recorder = new Recorder:
      override def afterAborted(tasks: Iterable[TaskId[?]]): Unit = throw failure
    val outcome =
      try Right(evaluate(recorder).result)
      catch case e: Throwable => Left(e)
    val kept = outcome match
      case Right(result @ TaskResult.Inc(i)) =>
        cancelled(result) && i.getSuppressed.contains(failure)
      case _ => false
    Result.assert(kept).log(s"outcome=$outcome")

  private def rethrownFailure: Result =
    val failure = new IllegalStateException("completion failure")
    val recorder = new Recorder:
      override def afterAborted(tasks: Iterable[TaskId[?]]): Unit = throw failure
    val x = new Execute(
      Execute.config(false),
      new Triggers(Map.empty, Map.empty, _ => throw failure),
      recorder
    )(using Transform(Transform.DummyTaskMap(Nil)))
    val pool = Executors.newCachedThreadPool()
    val service = ConcurrentRestrictions.completionService(
      pool,
      ConcurrentRestrictions.unrestricted,
      _ => ()
    )
    val outcome =
      try Right(x.run(task(()))(using service))
      catch case e: Throwable => Left(e)
      finally
        pool.shutdownNow()
        ()
    Result
      .assert(outcome == Left(failure) && failure.getSuppressed.isEmpty)
      .log(s"outcome=$outcome suppressed=${failure.getSuppressed.toList}")
  end rethrownFailure

  private def interruptedProgress: Result =
    val failure = new InterruptedException("progress interrupted")
    val recorder = new Recorder:
      override def afterAborted(tasks: Iterable[TaskId[?]]): Unit = throw failure
    val a = abort(recorder)
    Result.assert(suppressed(a, failure) && a.interrupted).log(a.toString)

  private def failingReporter: Result =
    val failure = new IllegalStateException("reporter failure")
    val failing = new Recorder:
      override def afterAborted(tasks: Iterable[TaskId[?]]): Unit = throw failure
    val recorder = new Recorder
    val a = abort(ExecuteProgress.aggregate(Seq(failing, recorder)))
    Result
      .assert(suppressed(a, failure) && recorder.aborted.size == 1)
      .log(s"$a aborted=${recorder.aborted}")

  private def reportInProgress: Result =
    val coordinator = Thread.currentThread()
    val entered = new CountDownLatch(1)
    val events = new ConcurrentLinkedQueue[String]
    val slow = task(())
    val recorder = new Recorder:
      override def afterWork[A](t: TaskId[A], r: Either[TaskId[A], TaskResult[A]]): Unit =
        super.afterWork(t, r)
        if t eq slow then
          entered.countDown()
          val deadline = System.nanoTime + TimeUnit.SECONDS.toNanos(10)
          while coordinator.getState != Thread.State.TIMED_WAITING && System.nanoTime < deadline do
            Thread.onSpinWait()
          events.add(s"afterWork returned while the abort was ${coordinator.getState}")
          ()
      override def afterAborted(tasks: Iterable[TaskId[?]]): Unit =
        events.add("afterAborted")
        super.afterAborted(tasks)
    val a = abort(recorder, Seq(slow), entered)
    val order = events.asScala.toVector
    Result
      .assert(
        a.outcome.exists(cancelled) && recorder.late.isEmpty &&
          order == Vector("afterWork returned while the abort was TIMED_WAITING", "afterAborted")
      )
      .log(s"$a order=$order late=${recorder.late}")
  end reportInProgress

  private def blockedReport: Result =
    val entered, release = new CountDownLatch(1)
    val returned = new AtomicReference[String]
    val stuck = task(())
    val recorder = new Recorder:
      override def afterWork[A](t: TaskId[A], r: Either[TaskId[A], TaskResult[A]]): Unit =
        if t eq stuck then
          entered.countDown()
          try returned.set(s"released=${release.await(10, TimeUnit.SECONDS)}")
          catch case _: InterruptedException => returned.set("interrupted")
    val a = abort(recorder, Seq(stuck), entered)
    release.countDown()
    Result
      .assert(
        a.outcome.exists(cancelled) && recorder.aborted.size == 1 && a.millis < 9000 &&
          !Option(returned.get).exists(_.startsWith("released"))
      )
      .log(s"$a aborted=${recorder.aborted.size} returned=${returned.get}")

  private def interruptedWait: Result =
    val coordinator = Thread.currentThread()
    val entered = new CountDownLatch(1)
    val events = new ConcurrentLinkedQueue[String]
    val slow = task(())
    def waitingUninterrupted = !coordinator.isInterrupted &&
      coordinator.getState == Thread.State.TIMED_WAITING
    val recorder = new Recorder:
      override def afterWork[A](t: TaskId[A], r: Either[TaskId[A], TaskResult[A]]): Unit =
        super.afterWork(t, r)
        if t eq slow then
          entered.countDown()
          val deadline = System.nanoTime + TimeUnit.SECONDS.toNanos(10)
          while !waitingUninterrupted && System.nanoTime < deadline do Thread.onSpinWait()
          coordinator.interrupt()
          while !waitingUninterrupted && System.nanoTime < deadline do Thread.onSpinWait()
          events.add(s"afterWork returned while the abort was ${coordinator.getState}")
          ()
      override def afterAborted(tasks: Iterable[TaskId[?]]): Unit =
        events.add("afterAborted")
        super.afterAborted(tasks)
    val a = abort(recorder, Seq(slow), entered)
    val order = events.asScala.toVector
    Result
      .assert(
        a.outcome.exists(cancelled) && a.interrupted && recorder.late.isEmpty &&
          order == Vector("afterWork returned while the abort was TIMED_WAITING", "afterAborted")
      )
      .log(s"$a order=$order late=${recorder.late}")
  end interruptedWait
end ExecuteAbortTest
