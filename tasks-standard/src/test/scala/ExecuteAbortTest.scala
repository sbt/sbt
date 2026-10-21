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
import hedgehog.runner._
import sbt.{
  CompletionService,
  Completed,
  ConcurrentRestrictions,
  Execute,
  ExecuteProgress,
  Inc,
  Result => TaskResult,
  Task
}
import sbt.internal.util.RMap
import sbt.std.TaskExtra._
import scala.collection.JavaConverters._

object ExecuteAbortTest extends Properties {
  override def tests: List[Test] = List(
    example("an aborted evaluation reports all of its tasks", abortedTasks),
    example("an aborted evaluation reports no work of still running tasks", lateWork),
    example("an abort keeps the original failure when progress fails", failedProgress),
  )

  private class Recorder extends ExecuteProgress[Task] {
    val registered = new ConcurrentLinkedQueue[Task[_]]
    val aborted = new ConcurrentLinkedQueue[Vector[Task[_]]]
    val late = new ConcurrentLinkedQueue[String]
    val completions = new AtomicInteger
    private def record(callback: String): Unit =
      if (!aborted.isEmpty) {
        late.add(callback)
        ()
      }
    override def initial(): Unit = ()
    override def afterRegistered(
        task: Task[_],
        allDeps: Iterable[Task[_]],
        pendingDeps: Iterable[Task[_]]
    ): Unit = {
      record(s"afterRegistered $task")
      registered.add(task)
      ()
    }
    override def afterReady(task: Task[_]): Unit = record(s"afterReady $task")
    override def beforeWork(task: Task[_]): Unit = record(s"beforeWork $task")
    override def afterWork[A](task: Task[A], result: Either[Task[A], TaskResult[A]]): Unit =
      record(s"afterWork $task")
    override def afterCompleted[A](task: Task[A], result: TaskResult[A]): Unit =
      record(s"afterCompleted $task")
    override def afterAllCompleted(results: RMap[Task, TaskResult]): Unit = {
      completions.incrementAndGet()
      ()
    }
    override private[sbt] def afterAborted(tasks: Iterable[Task[_]]): Unit = {
      aborted.add(tasks.toVector)
      ()
    }
    override def stop(): Unit = ()
  }

  private type Strategy = CompletionService[Task[_], Completed]

  private final case class Evaluation(
      outcome: Either[Throwable, TaskResult[_]],
      recorder: Recorder,
      lateRan: Boolean,
      idle: Boolean
  )

  private val sentinel = ConcurrentRestrictions.Tag("sentinel")
  private val afterRunning = ConcurrentRestrictions.Tag("after-running")

  private def execute(progress: ExecuteProgress[Task]) =
    new Execute[Task](Execute.config(false), Execute.noTriggers[Task], progress)(
      Transform(Transform.DummyTaskMap(Nil))
    )

  private def run(x: Execute[Task], root: Task[_], service: Strategy) =
    try Right(x.run(root)(service))
    catch { case e: Throwable => Left(e) }

  private def evaluate(recorder: Recorder): Evaluation = {
    val pool = Executors.newCachedThreadPool()
    val waitingStarted, runningStarted, release, lateRan = new CountDownLatch(1)
    val runningDone = new AtomicBoolean
    val service = ConcurrentRestrictions.completionService[Task[_], Completed](
      pool,
      ConcurrentRestrictions.tagged[Task[_]](
        _.tags,
        tags => tags.getOrElse(afterRunning, 0) == 0 || runningDone.get
      ),
      (_: String) => (),
      (t: Task[_]) => t.tags.contains(sentinel)
    )
    val waiting = task {
      waitingStarted.countDown()
      new CountDownLatch(1).await()
    }
    val trigger = task {
      waitingStarted.await()
      runningStarted.await()
      service.cancelSentinels()
    }
    val running = task {
      runningStarted.countDown()
      release.await()
      runningDone.set(true)
    }
    val late = task(lateRan.countDown()).tag(afterRunning)
    val root = Seq[Task[_]](waiting.tag(sentinel), trigger, running, late).join
    try {
      val outcome = run(execute(recorder), root, service)
      release.countDown()
      val ran = lateRan.await(10, TimeUnit.SECONDS)
      pool.shutdown()
      Evaluation(outcome, recorder, ran, pool.awaitTermination(10, TimeUnit.SECONDS))
    } finally {
      release.countDown()
      pool.shutdownNow()
      ()
    }
  }

  private def cancelled(outcome: Either[Throwable, TaskResult[_]]): Boolean = outcome match {
    case Right(Inc(i)) => i.message.contains("cancelled")
    case _             => false
  }

  private def abortedTasks: Result = {
    val e = evaluate(new Recorder)
    val reported = e.recorder.aborted.asScala.toVector
    val registered = e.recorder.registered.asScala.toSet
    Result
      .assert(
        cancelled(e.outcome) && reported.size == 1 && reported.head.toSet == registered &&
          reported.head.size == registered.size && registered.size > 4 &&
          e.recorder.completions.get == 0
      )
      .log(
        s"outcome=${e.outcome} reported=$reported registered=$registered " +
          s"completions=${e.recorder.completions.get}"
      )
  }

  private def lateWork: Result = {
    val e = evaluate(new Recorder)
    val late = e.recorder.late.asScala.toVector
    Result
      .assert(cancelled(e.outcome) && e.lateRan && e.idle && late.isEmpty)
      .log(s"outcome=${e.outcome} lateRan=${e.lateRan} idle=${e.idle} late=$late")
  }

  private def failedProgress: Result = {
    val failure = new IllegalStateException("progress failure")
    val recorder = new Recorder {
      override private[sbt] def afterAborted(tasks: Iterable[Task[_]]): Unit = throw failure
    }
    val outcome = evaluate(recorder).outcome
    val kept = outcome match {
      case Right(Inc(i)) => cancelled(outcome) && i.getSuppressed.contains(failure)
      case _             => false
    }
    Result.assert(kept).log(s"outcome=$outcome")
  }
}
