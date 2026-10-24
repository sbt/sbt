/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

import java.util.concurrent.{
  Callable,
  CancellationException,
  CountDownLatch,
  ExecutorService,
  Executors,
  TimeUnit,
  TimeoutException
}
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicInteger, AtomicReference }
import hedgehog.Result
import hedgehog.runner._
import sbt.{
  Completed,
  ConcurrentRestrictions,
  Execute,
  ExecuteProgress,
  ExecuteProgress2,
  ExecuteProgressAdapter,
  Result => TaskResult,
  Task,
  Value
}
import sbt.internal.util.{ PMap, Util }
import sbt.std.Transform
import sbt.std.TaskExtra._
import sbt.util.Logger
import scala.concurrent.duration._

object TaskProgressLifecycleTest extends Properties {
  override def tests: List[Test] = List(
    example("completed evaluations release task metadata", completedEvaluations),
    example("completion cannot overtake a queued ready callback", delayedReady),
    example("completed evaluations remove cancelled schedules", cancelledSchedules),
    example("report requests do not accumulate while progress is busy", coalescedReports),
    example("completing an inner evaluation preserves an outer task", nestedEvaluation),
    example("already completed continuations release task metadata", completedContinuation),
    example("closing progress releases unfinished task metadata", unfinishedTasks),
    example("interrupted completion preserves the flag and queued cleanup", interruptedCompletion),
    example("interrupted completion keeps the timer of a reused task", interruptedReuse),
    example("timed-out completion returns and keeps the queued cleanup", timedOutCompletion),
    example(
      "completion propagates the original exception",
      failedCompletion(new IllegalStateException("progress failure"))
    ),
    example(
      "completion propagates the original error",
      failedCompletion(new AssertionError("progress failure"))
    ),
    example(
      "completion propagates a cancellation thrown by cleanup",
      failedCompletion(new CancellationException("progress failure"))
    ),
    example(
      "completion propagates a timeout thrown by cleanup",
      failedCompletion(new TimeoutException("progress failure"))
    ),
    example("closing progress releases a waiting completion", closedCompletion),
    example("aborted evaluations release task metadata", abortedEvaluations),
    example("aborting an inner evaluation preserves an outer task", nestedAbort),
    example("an abort reaches task progress after failing reporters", abortAfterFailures),
    example("an abort stops the timers of the aborted tasks", abortedTimers),
    example(
      "a late work report of an earlier reporter stays out of task progress",
      lateWorkReport(commandAggregate)
    ),
    example(
      "a late work report of an earlier aggregated reporter stays out of task progress",
      lateWorkReport(progressAggregate)
    ),
    example(
      "a late work report of an earlier reporter keeps a reused task's timer",
      reusedTask(commandAggregate)
    ),
    example(
      "a late work report of an earlier aggregated reporter keeps a reused task's timer",
      reusedTask(progressAggregate)
    ),
  )

  private def progress() = new TaskProgress(1.hour, 1.hour, Logger.Null)

  private def using[A <: AutoCloseable, B](resource: A)(f: A => B): B =
    try f(resource)
    finally resource.close()

  private val metadata = Set("active", "anonOwners", "calledBy", "taskNameCache", "timings")

  private def keys(progress: TaskProgress): Map[String, Set[Task[_]]] = {
    val maps = progress.retainedTaskMetadata
    assert(maps.keySet == metadata, s"unexpected task progress metadata: ${maps.keySet}")
    maps
  }

  private def retained(progress: TaskProgress): Map[String, Int] =
    keys(progress).collect { case (name, tasks) if tasks.nonEmpty => name -> tasks.size }

  private def finish(progress: TaskProgress, t: Task[Unit]): Unit = {
    progress.afterReady(t)
    progress.beforeWork(t)
    progress.afterWork(t, Right(Value(())))
    progress.afterCompleted(t, Value(()))
  }

  private def complete(progress: TaskProgress, tasks: Task[Unit]*): Unit = {
    val results = PMap.empty[Task, TaskResult]
    tasks.foreach(t => results(t) = Value(()))
    progress.afterAllCompleted(results)
    progress.awaitIdle()
  }

  private def completedEvaluations: Result =
    using(progress()) { p =>
      val states = (1 to 10).map { _ =>
        val owner = task(()).named("compile")
        val dependency, called = task(())
        p.afterRegistered(owner, List(dependency), List(dependency))
        finish(p, dependency)
        p.afterReady(owner)
        p.beforeWork(owner)
        p.afterWork(owner, Left(called))
        finish(p, called)
        p.afterCompleted(owner, Value(()))
        complete(p, owner, dependency, called)
        retained(p)
      }
      Result.assert(states.forall(_.isEmpty)).log(states.toString)
    }

  private def delayedReady: Result = {
    val entered, release = new CountDownLatch(1)
    val observer = Thread.currentThread()
    val t = task(())
    val p = new TaskProgress(1.hour, 1.hour, Logger.Null) {
      override protected def taskName(t: Task[_]): String = {
        if (Thread.currentThread() ne observer) {
          entered.countDown()
          release.await()
        }
        super.taskName(t)
      }
      override def close(): Unit = {
        release.countDown()
        super.close()
      }
    }
    using(p) { p =>
      p.afterReady(t)
      assert(entered.await(10, TimeUnit.SECONDS))
      p.beforeWork(t)
      p.afterWork(t, Right(Value(())))
      p.afterCompleted(t, Value(()))
      release.countDown()
      p.awaitIdle()
      Result.assert(retained(p).isEmpty).log(retained(p).toString)
    }
  }

  private def cancelledSchedules: Result =
    using(progress()) { p =>
      val sizes = (1 to 10).map { _ =>
        val t = task(())
        p.afterReady(t)
        p.beforeWork(t)
        p.awaitIdle()
        val running = p.scheduledReports
        p.afterWork(t, Right(Value(())))
        p.afterCompleted(t, Value(()))
        complete(p, t)
        running -> p.scheduledReports
      }
      Result.assert(sizes.forall(_ == ((2, 0)))).log(sizes.toString)
    }

  private def coalescedReports: Result = {
    val entered, release = new CountDownLatch(1)
    val observer = Thread.currentThread()
    val blocker = task(())
    val p = new TaskProgress(1.millis, 1.hour, Logger.Null) {
      override protected def taskName(t: Task[_]): String = {
        if ((t eq blocker) && (Thread.currentThread() ne observer)) {
          entered.countDown()
          release.await()
        }
        super.taskName(t)
      }
      override def close(): Unit = {
        release.countDown()
        super.close()
      }
    }
    using(p) { p =>
      p.afterReady(blocker)
      assert(entered.await(10, TimeUnit.SECONDS))
      val ticks = p.scheduledReportRuns + 100
      p.beforeWork(task(()))
      val deadline = 10.seconds.fromNow
      while (p.scheduledReportRuns < ticks && deadline.hasTimeLeft()) Thread.sleep(10)
      val ticked = p.scheduledReportRuns >= ticks
      val queued = p.queuedCallbacks
      release.countDown()
      Result.assert(ticked && queued == 1).log(s"ticked=$ticked queued=$queued")
    }
  }

  private def nestedEvaluation: Result =
    using(progress()) { p =>
      val outer = task(()).named("outer")
      val owner = task(()).named("inner")
      val called = task(())
      p.afterReady(outer)
      p.beforeWork(outer)
      finish(p, called)
      p.awaitIdle()
      p.afterReady(owner)
      p.beforeWork(owner)
      p.afterWork(owner, Left(called))
      p.afterCompleted(owner, Value(()))
      complete(p, owner, called)
      val timed = p.activeTasks(System.nanoTime()).map(_._1)
      val displayed = keys(p)("active")
      val inner = keys(p).values.flatten.filter(Set[Task[_]](owner, called))
      p.afterWork(outer, Right(Value(())))
      p.afterCompleted(outer, Value(()))
      complete(p, outer)
      Result
        .assert(
          timed == Vector(outer) && displayed == Set(outer) && inner.isEmpty &&
            retained(p).isEmpty
        )
        .log(s"timed=$timed displayed=$displayed inner=$inner retained=${retained(p)}")
    }

  private def completedContinuation: Result =
    using(progress()) { p =>
      val owner = task(()).named("compile")
      val called = task(())
      finish(p, called)
      p.awaitIdle()
      p.afterReady(owner)
      p.beforeWork(owner)
      p.afterWork(owner, Left(called))
      p.afterCompleted(owner, Value(()))
      complete(p, owner, called)
      Result.assert(retained(p).isEmpty).log(retained(p).toString)
    }

  private def unfinishedTasks: Result =
    using(progress()) { p =>
      val owner, dependency = task(())
      p.afterRegistered(owner, List(dependency), List(dependency))
      p.afterReady(dependency)
      p.beforeWork(dependency)
      p.close()
      Result.assert(retained(p).isEmpty).log(retained(p).toString)
    }

  private def thrownBy(action: => Unit): Option[Throwable] =
    try {
      action
      None
    } catch { case e: Throwable => Some(e) }

  private def interruptedCompletion: Result = {
    val entered, release = new CountDownLatch(1)
    val owner, dependency = task(())
    val p = new TaskProgress(1.hour, 1.hour, Logger.Null) {
      override protected def clearTask(t: Task[_]): Unit = {
        entered.countDown()
        release.await()
        super.clearTask(t)
      }
      override def close(): Unit = {
        release.countDown()
        super.close()
      }
    }
    using(p) { p =>
      p.afterRegistered(owner, List(dependency), List(dependency))
      val results = PMap.empty[Task, TaskResult]
      results(dependency) = Value(())
      Thread.currentThread().interrupt()
      val (thrown, interrupted) =
        try {
          val thrown = thrownBy(p.afterAllCompleted(results))
          (thrown, Thread.currentThread().isInterrupted)
        } finally Util.ignoreResult(Thread.interrupted())
      assert(entered.await(10, TimeUnit.SECONDS))
      release.countDown()
      p.awaitIdle()
      Result
        .assert(thrown.isEmpty && interrupted && retained(p).isEmpty)
        .log(s"thrown=$thrown interrupted=$interrupted retained=${retained(p)}")
    }
  }

  private def interruptedReuse: Result = {
    val entered, release = new CountDownLatch(1)
    val observer = Thread.currentThread()
    val blocker = task(())
    val p = new TaskProgress(1.hour, 1.hour, Logger.Null) {
      override protected def taskName(t: Task[_]): String = {
        if ((t eq blocker) && (Thread.currentThread() ne observer)) {
          entered.countDown()
          release.await()
        }
        super.taskName(t)
      }
      override def close(): Unit = {
        release.countDown()
        super.close()
      }
    }
    using(p) { p =>
      val t = task(())
      finish(p, t)
      p.awaitIdle()
      p.afterReady(blocker)
      assert(entered.await(10, TimeUnit.SECONDS))
      val results = PMap.empty[Task, TaskResult]
      results(t) = Value(())
      Thread.currentThread().interrupt()
      try p.afterAllCompleted(results)
      finally Util.ignoreResult(Thread.interrupted())
      p.afterReady(t)
      p.beforeWork(t)
      release.countDown()
      p.awaitIdle()
      val timed = p.activeTasks(System.nanoTime()).map(_._1)
      p.afterWork(t, Right(Value(())))
      p.afterCompleted(t, Value(()))
      p.afterCompleted(blocker, Value(()))
      complete(p, t, blocker)
      Result
        .assert(timed == Vector(t) && retained(p).isEmpty)
        .log(s"timed=$timed retained=${retained(p)}")
    }
  }

  private def timedOutCompletion: Result = {
    val entered, release = new CountDownLatch(1)
    val observer = Thread.currentThread()
    val blocker = task(())
    val p = new TaskProgress(1.hour, 1.hour, Logger.Null) {
      override protected def completionTimeout: FiniteDuration = 100.millis
      override protected def taskName(t: Task[_]): String = {
        if ((t eq blocker) && (Thread.currentThread() ne observer)) {
          entered.countDown()
          release.await()
        }
        super.taskName(t)
      }
      override def close(): Unit = {
        release.countDown()
        super.close()
      }
    }
    using(p) { p =>
      val owner, dependency = task(())
      p.afterRegistered(owner, List(dependency), List(dependency))
      p.afterReady(blocker)
      assert(entered.await(10, TimeUnit.SECONDS))
      val results = PMap.empty[Task, TaskResult]
      results(dependency) = Value(())
      val thrown = new AtomicReference[Throwable]
      val interrupted = new AtomicBoolean
      val waiter = new Thread(() => {
        thrownBy(p.afterAllCompleted(results)).foreach(thrown.set)
        interrupted.set(Thread.currentThread().isInterrupted)
      })
      waiter.setDaemon(true)
      waiter.start()
      waiter.join(5.seconds.toMillis)
      val returned = !waiter.isAlive
      val queued = retained(p)
      release.countDown()
      p.awaitIdle()
      p.afterCompleted(blocker, Value(()))
      complete(p, blocker)
      waiter.join(10.seconds.toMillis)
      Result
        .assert(
          returned && thrown.get == null && !interrupted.get &&
            queued == Map("anonOwners" -> 1) && retained(p).isEmpty
        )
        .log(
          s"returned=$returned thrown=${Option(thrown.get)} interrupted=${interrupted.get} " +
            s"queued=$queued retained=${retained(p)}"
        )
    }
  }

  private def failedCompletion(cause: Throwable): Result = {
    val p = new TaskProgress(1.hour, 1.hour, Logger.Null) {
      override protected def clearTask(t: Task[_]): Unit = throw cause
    }
    using(p) { p =>
      val results = PMap.empty[Task, TaskResult]
      results(task(())) = Value(())
      val thrown = thrownBy(p.afterAllCompleted(results))
      Result.assert(thrown.contains(cause)).log(s"expected=$cause thrown=$thrown")
    }
  }

  private def closedCompletion: Result = {
    val entered, blocked = new CountDownLatch(1)
    val observer = Thread.currentThread()
    val p = new TaskProgress(1.hour, 1.hour, Logger.Null) {
      override protected def completionTimeout: FiniteDuration = 1.hour
      override protected def taskName(t: Task[_]): String = {
        if (Thread.currentThread() ne observer) {
          entered.countDown()
          try blocked.await()
          catch { case _: InterruptedException => () }
        }
        super.taskName(t)
      }
    }
    val thrown = new AtomicReference[Throwable]
    val waiter = new Thread(
      () => thrownBy(p.afterAllCompleted(PMap.empty[Task, TaskResult])).foreach(thrown.set)
    )
    waiter.setDaemon(true)
    try {
      using(p) { p =>
        p.afterReady(task(()))
        assert(entered.await(10, TimeUnit.SECONDS))
        waiter.start()
        val deadline = 10.seconds.fromNow
        def waiting = waiter.getState == Thread.State.TIMED_WAITING
        while (!waiting && deadline.hasTimeLeft()) Thread.sleep(10)
        val waitingBeforeClose = waiting
        p.close()
        waiter.join(10.seconds.toMillis)
        Result
          .assert(waitingBeforeClose && !waiter.isAlive && thrown.get == null)
          .log(
            s"waitingBeforeClose=$waitingBeforeClose alive=${waiter.isAlive} " +
              s"state=${waiter.getState} thrown=${Option(thrown.get)}"
          )
      }
    } finally {
      blocked.countDown()
      waiter.interrupt()
      waiter.join(10.seconds.toMillis)
    }
  }

  private def abortedEvaluations: Result =
    using(progress()) { p =>
      val states = (1 to 10).map { _ =>
        val owner = task(()).named("compile")
        val dependency, continuing, called, done = task(())
        val deps = List(dependency, continuing, done)
        p.afterRegistered(owner, deps, deps)
        finish(p, done)
        p.afterReady(dependency)
        p.beforeWork(dependency)
        p.afterReady(continuing)
        p.beforeWork(continuing)
        p.afterWork(continuing, Left(called))
        p.awaitIdle()
        val scheduled = p.scheduledReports
        p.afterAborted(owner :: deps)
        p.awaitIdle()
        (scheduled, p.scheduledReports, retained(p))
      }
      Result
        .assert(states.forall {
          case (before, after, left) => before == 3 && after == 0 && left.isEmpty
        })
        .log(states.toString)
    }

  private def nestedAbort: Result =
    using(progress()) { p =>
      val outerOwner = task(()).named("outer-owner")
      val outer = task(()).named("outer")
      val owner = task(()).named("inner")
      val dependency = task(())
      p.afterRegistered(outerOwner, List(outer), List(outer))
      p.afterReady(outer)
      p.beforeWork(outer)
      p.afterRegistered(owner, List(dependency), List(dependency))
      p.afterReady(dependency)
      p.beforeWork(dependency)
      p.afterAborted(List(owner, dependency))
      val timed = p.activeTasks(System.nanoTime()).map(_._1)
      val displayed = keys(p)("active")
      val owners = keys(p)("anonOwners")
      val inner = keys(p).values.flatten.filter(Set[Task[_]](owner, dependency))
      p.afterWork(outer, Right(Value(())))
      p.afterCompleted(outer, Value(()))
      p.afterCompleted(outerOwner, Value(()))
      complete(p, outer, outerOwner)
      Result
        .assert(
          timed == Vector(outer) && displayed == Set(outer) && owners == Set(outer) &&
            inner.isEmpty && retained(p).isEmpty
        )
        .log(
          s"timed=$timed displayed=$displayed owners=$owners inner=$inner retained=${retained(p)}"
        )
    }

  private def failing(failure: Throwable): ExecuteProgressAdapter =
    new ExecuteProgressAdapter(ExecuteProgress.empty[Task]) {
      override def afterAborted(tasks: Iterable[Task[_]]): Unit = throw failure
    }

  private def abortAfterFailures: Result =
    using(progress()) { p =>
      val first = new IllegalStateException("first reporter failure")
      val second = new IllegalStateException("second reporter failure")
      val reporters = ExecuteProgress2.aggregate(
        Seq(
          failing(first),
          new ExecuteProgressAdapter(ExecuteProgress.aggregate[Task](Seq(failing(second), p)))
        )
      )
      val owner = task(()).named("compile")
      val dependency, continuing, called = task(())
      val deps = List(dependency, continuing)
      p.afterRegistered(owner, deps, deps)
      p.afterReady(dependency)
      p.beforeWork(dependency)
      p.afterReady(continuing)
      p.beforeWork(continuing)
      p.afterWork(continuing, Left(called))
      val thrown = thrownBy(reporters.afterAborted(owner :: deps))
      p.awaitIdle()
      Result
        .assert(
          thrown.contains(first) && first.getSuppressed.toList == List(second) &&
            retained(p).isEmpty
        )
        .log(s"thrown=$thrown suppressed=${first.getSuppressed.toList} retained=${retained(p)}")
    }

  private def abortedTimers: Result = {
    val timings = new TaskTimings(reportOnShutdown = false, Logger.Null)
    val aborted, other = task(())
    timings.beforeWork(aborted)
    timings.beforeWork(other)
    timings.afterAborted(List(aborted))
    val states: Map[Task[_], Boolean] =
      timings.currentTimings.map { case (t, timer) => (t: Task[_]) -> timer.isActive }.toMap
    val duration = timings.currentTimings.collectFirst {
      case (t, timer) if t eq aborted => timer.durationNanos
    }
    Result
      .assert(states == Map(aborted -> false, other -> true) && duration.exists(_ >= 0))
      .log(s"states=$states duration=$duration")
  }

  private val sentinel = ConcurrentRestrictions.Tag("sentinel")

  private def evaluation(
      progress: ExecuteProgress[Task],
      root: Task[_],
      service: sbt.CompletionService[Task[_], Completed]
  ): Callable[Either[Throwable, TaskResult[_]]] =
    () =>
      try Right(
        new Execute[Task](Execute.config(false), Execute.noTriggers[Task], progress)(
          Transform(Transform.DummyTaskMap(Nil))
        ).run(root)(service)
      )
      catch { case e: Throwable => Left(e) }

  private def cancelAfter(
      entered: CountDownLatch,
      progress: ExecuteProgress[Task],
      t: Task[Unit],
      pool: ExecutorService
  ): Callable[Either[Throwable, TaskResult[_]]] = {
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
      entered.await()
      service.cancelSentinels()
    }
    evaluation(progress, Seq[Task[_]](waiting.tag(sentinel), trigger, t).join, service)
  }

  private def cancelled(outcome: Either[Throwable, TaskResult[_]]): Boolean = outcome match {
    case Left(_: CancellationException) => true
    case _                              => false
  }

  private def drained(pool: ExecutorService): Boolean = {
    pool.shutdown()
    pool.awaitTermination(10, TimeUnit.SECONDS)
  }

  private def commandAggregate(reporters: Seq[ExecuteProgress[Task]]): ExecuteProgress[Task] =
    ExecuteProgress2.aggregate(reporters.map(new ExecuteProgressAdapter(_)))

  private def progressAggregate(reporters: Seq[ExecuteProgress[Task]]): ExecuteProgress[Task] =
    ExecuteProgress.aggregate[Task](reporters)

  private def lateWorkReport(
      aggregate: Seq[ExecuteProgress[Task]] => ExecuteProgress[Task]
  ): Result =
    using(progress()) { p =>
      val timings = new TaskTimings(reportOnShutdown = false, Logger.Null)
      val entered, release, returned = new CountDownLatch(1)
      val stuck = task(())
      val blocker = new ExecuteProgressAdapter(ExecuteProgress.empty[Task]) {
        override def beforeWork(t: Task[_]): Unit =
          if (t eq stuck) {
            entered.countDown()
            release.await()
            returned.countDown()
          }
      }
      val reporters = aggregate(Seq(blocker, p, timings))
      val pool, coordinator = Executors.newCachedThreadPool()
      try {
        val outcome = coordinator
          .submit(cancelAfter(entered, reporters, stuck, pool))
          .get(30, TimeUnit.SECONDS)
        p.awaitIdle()
        val cleaned = retained(p)
        val held = returned.getCount == 1
        release.countDown()
        val done = drained(pool)
        p.awaitIdle()
        val running = timings.currentTimings.collect {
          case (t, timer) if timer.isActive => t
        }.toList
        Result
          .assert(
            cancelled(outcome) && cleaned.isEmpty && held && done && retained(p).isEmpty &&
              p.scheduledReports == 0 && running.isEmpty
          )
          .log(
            s"outcome=$outcome cleaned=$cleaned held=$held done=$done retained=${retained(p)} " +
              s"scheduled=${p.scheduledReports} running=$running"
          )
      } finally {
        release.countDown()
        Seq(pool, coordinator).foreach(_.shutdownNow())
      }
    }

  private def reusedTask(aggregate: Seq[ExecuteProgress[Task]] => ExecuteProgress[Task]): Result =
    using(progress()) { p =>
      val timings = new TaskTimings(reportOnShutdown = false, Logger.Null)
      val entered, release, returned, started, finish = new CountDownLatch(1)
      val runs = new AtomicInteger
      val reused = task {
        if (runs.incrementAndGet() == 2) {
          started.countDown()
          finish.await()
        }
      }
      val blocking = new AtomicBoolean(true)
      val blocker = new ExecuteProgressAdapter(ExecuteProgress.empty[Task]) {
        override def afterWork[A](t: Task[A], r: Either[Task[A], TaskResult[A]]): Unit =
          if ((t eq reused) && blocking.getAndSet(false)) {
            entered.countDown()
            release.await()
            returned.countDown()
          }
      }
      val reporters = aggregate(Seq(blocker, p, timings))
      val first, second, coordinator = Executors.newCachedThreadPool()
      try {
        val aborted = coordinator
          .submit(cancelAfter(entered, reporters, reused, first))
          .get(30, TimeUnit.SECONDS)
        val service = ConcurrentRestrictions.completionService[Task[_], Completed](
          second,
          ConcurrentRestrictions.unrestricted[Task[_]],
          (_: String) => ()
        )
        val next = coordinator.submit(evaluation(reporters, reused, service))
        val running = started.await(30, TimeUnit.SECONDS)
        val held = returned.getCount == 1
        release.countDown()
        val done = drained(first)
        val timed = p.activeTasks(System.nanoTime()).map(_._1)
        val traced = timings.currentTimings.collect {
          case (t, timer) if t eq reused => timer.isActive
        }.toList
        finish.countDown()
        val completed = next.get(30, TimeUnit.SECONDS)
        p.awaitIdle()
        Result
          .assert(
            cancelled(aborted) && running && held && done && timed == Vector(reused) &&
              traced == List(true) && completed == Right(Value(())) && retained(p).isEmpty
          )
          .log(
            s"aborted=$aborted running=$running held=$held done=$done timed=$timed " +
              s"traced=$traced completed=$completed retained=${retained(p)}"
          )
      } finally {
        release.countDown()
        finish.countDown()
        Seq(first, second, coordinator).foreach(_.shutdownNow())
      }
    }
}
