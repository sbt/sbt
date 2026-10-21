/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.std

import java.util.concurrent.{
  CancellationException,
  ConcurrentLinkedQueue,
  CountDownLatch,
  Executors,
  TimeUnit
}
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicInteger, AtomicReference }
import hedgehog.Result
import hedgehog.runner._
import sbt.{
  CompletionService,
  Completed,
  ConcurrentRestrictions,
  Execute,
  ExecuteProgress,
  Result => TaskResult,
  Task,
  Triggers
}
import sbt.internal.util.RMap
import sbt.std.TaskExtra._
import scala.collection.JavaConverters._

object ExecuteAbortTest extends Properties {
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
    override def afterAborted(tasks: Iterable[Task[_]]): Unit = {
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

  private def execute(progress: ExecuteProgress[Task], triggers: Triggers[Task]) =
    new Execute[Task](Execute.config(false), triggers, progress)(
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
      val outcome = run(execute(recorder, Execute.noTriggers[Task]), root, service)
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

  private final case class Abort(
      outcome: Either[Throwable, TaskResult[_]],
      interrupted: Boolean,
      millis: Long
  )

  private def abort(
      progress: ExecuteProgress[Task],
      tasks: Seq[Task[_]] = Nil,
      before: CountDownLatch = new CountDownLatch(0)
  ): Abort = {
    val pool = Executors.newCachedThreadPool()
    val waitingStarted = new CountDownLatch(1)
    val service = ConcurrentRestrictions.completionService[Task[_], Completed](
      pool,
      ConcurrentRestrictions.unrestricted[Task[_]],
      (_: String) => (),
      (t: Task[_]) => t.tags.contains(sentinel)
    )
    val waiting = task {
      waitingStarted.countDown()
      new CountDownLatch(1).await()
    }
    val trigger = task {
      waitingStarted.await()
      before.await()
      service.cancelSentinels()
    }
    val root = (Seq[Task[_]](waiting.tag(sentinel), trigger) ++ tasks).join
    val start = System.nanoTime
    try {
      val outcome = run(execute(progress, Execute.noTriggers[Task]), root, service)
      Abort(outcome, Thread.interrupted(), (System.nanoTime - start) / 1000000)
    } finally {
      pool.shutdownNow()
      ()
    }
  }

  private def cancelled(outcome: Either[Throwable, TaskResult[_]]): Boolean = outcome match {
    case Left(_: CancellationException) => true
    case _                              => false
  }

  private def suppressed(abort: Abort, failure: Throwable): Boolean = abort.outcome match {
    case Left(c: CancellationException) => c.getSuppressed.contains(failure)
    case _                              => false
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
      override def afterAborted(tasks: Iterable[Task[_]]): Unit = throw failure
    }
    val outcome = evaluate(recorder).outcome
    val kept = outcome match {
      case Left(c: CancellationException) => c.getSuppressed.contains(failure)
      case _                              => false
    }
    Result.assert(kept).log(s"outcome=$outcome")
  }

  private def rethrownFailure: Result = {
    val failure = new IllegalStateException("completion failure")
    val recorder = new Recorder {
      override def afterAborted(tasks: Iterable[Task[_]]): Unit = throw failure
    }
    val x = execute(recorder, new Triggers[Task](Map.empty, Map.empty, _ => throw failure))
    val pool = Executors.newCachedThreadPool()
    val service = ConcurrentRestrictions.completionService[Task[_], Completed](
      pool,
      ConcurrentRestrictions.unrestricted[Task[_]],
      (_: String) => ()
    )
    val outcome =
      try run(x, task(()), service)
      finally {
        pool.shutdownNow()
        ()
      }
    Result
      .assert(outcome == Left(failure) && failure.getSuppressed.isEmpty)
      .log(s"outcome=$outcome suppressed=${failure.getSuppressed.toList}")
  }

  private def interruptedProgress: Result = {
    val failure = new InterruptedException("progress interrupted")
    val recorder = new Recorder {
      override def afterAborted(tasks: Iterable[Task[_]]): Unit = throw failure
    }
    val a = abort(recorder)
    Result.assert(suppressed(a, failure) && a.interrupted).log(a.toString)
  }

  private def failingReporter: Result = {
    val failure = new IllegalStateException("reporter failure")
    val failing = new Recorder {
      override def afterAborted(tasks: Iterable[Task[_]]): Unit = throw failure
    }
    val recorder = new Recorder
    val a = abort(ExecuteProgress.aggregate[Task](Seq(failing, recorder)))
    Result
      .assert(suppressed(a, failure) && recorder.aborted.size == 1)
      .log(s"$a aborted=${recorder.aborted}")
  }

  private def spinUntil(done: => Boolean, deadline: Long): Unit =
    while (!done && System.nanoTime < deadline) Thread.`yield`()

  private def reportInProgress: Result = {
    val coordinator = Thread.currentThread()
    val entered = new CountDownLatch(1)
    val events = new ConcurrentLinkedQueue[String]
    val slow = task(())
    val recorder = new Recorder {
      override def afterWork[A](t: Task[A], r: Either[Task[A], TaskResult[A]]): Unit = {
        super.afterWork(t, r)
        if (t eq slow) {
          entered.countDown()
          val deadline = System.nanoTime + TimeUnit.SECONDS.toNanos(10)
          spinUntil(coordinator.getState == Thread.State.TIMED_WAITING, deadline)
          events.add(s"afterWork returned while the abort was ${coordinator.getState}")
          ()
        }
      }
      override def afterAborted(tasks: Iterable[Task[_]]): Unit = {
        events.add("afterAborted")
        super.afterAborted(tasks)
      }
    }
    val a = abort(recorder, Seq(slow), entered)
    val order = events.asScala.toVector
    Result
      .assert(
        cancelled(a.outcome) && recorder.late.isEmpty &&
          order == Vector("afterWork returned while the abort was TIMED_WAITING", "afterAborted")
      )
      .log(s"$a order=$order late=${recorder.late}")
  }

  private def blockedReport: Result = {
    val entered, release = new CountDownLatch(1)
    val returned = new AtomicReference[String]
    val stuck = task(())
    val recorder = new Recorder {
      override def afterWork[A](t: Task[A], r: Either[Task[A], TaskResult[A]]): Unit =
        if (t eq stuck) {
          entered.countDown()
          try returned.set(s"released=${release.await(10, TimeUnit.SECONDS)}")
          catch { case _: InterruptedException => returned.set("interrupted") }
        }
    }
    val a = abort(recorder, Seq(stuck), entered)
    release.countDown()
    Result
      .assert(
        cancelled(a.outcome) && recorder.aborted.size == 1 && a.millis < 9000 &&
          !Option(returned.get).exists(_.startsWith("released"))
      )
      .log(s"$a aborted=${recorder.aborted.size} returned=${returned.get}")
  }

  private def interruptedWait: Result = {
    val coordinator = Thread.currentThread()
    val entered = new CountDownLatch(1)
    val events = new ConcurrentLinkedQueue[String]
    val slow = task(())
    def waitingUninterrupted =
      !coordinator.isInterrupted && coordinator.getState == Thread.State.TIMED_WAITING
    val recorder = new Recorder {
      override def afterWork[A](t: Task[A], r: Either[Task[A], TaskResult[A]]): Unit = {
        super.afterWork(t, r)
        if (t eq slow) {
          entered.countDown()
          val deadline = System.nanoTime + TimeUnit.SECONDS.toNanos(10)
          spinUntil(waitingUninterrupted, deadline)
          coordinator.interrupt()
          spinUntil(waitingUninterrupted, deadline)
          events.add(s"afterWork returned while the abort was ${coordinator.getState}")
          ()
        }
      }
      override def afterAborted(tasks: Iterable[Task[_]]): Unit = {
        events.add("afterAborted")
        super.afterAborted(tasks)
      }
    }
    val a = abort(recorder, Seq(slow), entered)
    val order = events.asScala.toVector
    Result
      .assert(
        cancelled(a.outcome) && a.interrupted && recorder.late.isEmpty &&
          order == Vector("afterWork returned while the abort was TIMED_WAITING", "afterAborted")
      )
      .log(s"$a order=$order late=${recorder.late}")
  }
}
