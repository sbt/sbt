/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.std

import java.util.concurrent.{ ConcurrentLinkedQueue, CountDownLatch, Executors, TimeUnit }
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicInteger }
import hedgehog.Result
import hedgehog.runner.*
import sbt.{ ConcurrentRestrictions, Execute, ExecuteProgress, Result as TaskResult, TaskId }
import sbt.internal.util.RMap
import sbt.std.TaskExtra.*
import scala.jdk.CollectionConverters.*

object ExecuteAbortTest extends Properties:
  override def tests: List[Test] = List(
    example("an aborted evaluation reports all of its tasks", abortedTasks),
    example("an aborted evaluation reports no work of still running tasks", lateWork),
    example("an abort keeps the original failure when progress fails", failedProgress),
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
    override private[sbt] def afterAborted(tasks: Iterable[TaskId[?]]): Unit =
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
      override private[sbt] def afterAborted(tasks: Iterable[TaskId[?]]): Unit = throw failure
    val outcome =
      try Right(evaluate(recorder).result)
      catch case e: Throwable => Left(e)
    val kept = outcome match
      case Right(result @ TaskResult.Inc(i)) =>
        cancelled(result) && i.getSuppressed.contains(failure)
      case _ => false
    Result.assert(kept).log(s"outcome=$outcome")
end ExecuteAbortTest
