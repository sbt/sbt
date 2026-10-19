/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

import java.util.concurrent.{ CancellationException, CountDownLatch, ThreadPoolExecutor, TimeUnit }
import java.util.concurrent.atomic.AtomicReference
import hedgehog.Result
import hedgehog.runner._
import sbt.{ Result => TaskResult, Task, Value }
import sbt.internal.util.{ PMap, Util }
import sbt.std.TaskExtra._
import sbt.util.Logger
import scala.collection.JavaConverters._
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
    example("closing progress releases a waiting completion", closedCompletion),
  )

  private def progress() = new TaskProgress(1.hour, 1.hour, Logger.Null)

  private def using[A <: AutoCloseable, B](resource: A)(f: A => B): B =
    try f(resource)
    finally resource.close()

  private def fields(value: AnyRef): Iterator[(String, AnyRef)] =
    Iterator
      .iterate[Class[_]](value.getClass)(_.getSuperclass)
      .takeWhile(_ != null)
      .flatMap(_.getDeclaredFields)
      .map { field =>
        field.setAccessible(true)
        field.getName.split("\\$").last -> field.get(value)
      }

  private def pool(progress: TaskProgress, name: String): ThreadPoolExecutor =
    fields(progress).collectFirst { case (`name`, e: ThreadPoolExecutor) => e }.get

  private def awaitCallbacks(progress: TaskProgress): Unit =
    Util.ignoreResult(
      pool(progress, "executor").submit((() => ()): Runnable).get(10, TimeUnit.SECONDS)
    )

  private val metadata = Set("active", "anonOwners", "calledBy", "taskNameCache", "timings")

  private def keys(progress: TaskProgress): Map[String, Set[Any]] = {
    val maps = fields(progress).collect {
      case (name, map: java.util.Map[_, _]) => name -> map.keySet.asScala.toSet[Any]
    }.toMap
    assert(
      metadata.subsetOf(maps.keySet),
      s"task progress maps not found: ${metadata -- maps.keySet}"
    )
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
    awaitCallbacks(progress)
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
      awaitCallbacks(p)
      Result.assert(retained(p).isEmpty).log(retained(p).toString)
    }
  }

  private def cancelledSchedules: Result =
    using(progress()) { p =>
      val scheduled = pool(p, "scheduler").getQueue
      val sizes = (1 to 10).map { _ =>
        val t = task(())
        p.afterReady(t)
        p.beforeWork(t)
        awaitCallbacks(p)
        val running = scheduled.size
        p.afterWork(t, Right(Value(())))
        p.afterCompleted(t, Value(()))
        complete(p, t)
        running -> scheduled.size
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
      val scheduler = pool(p, "scheduler")
      p.afterReady(blocker)
      assert(entered.await(10, TimeUnit.SECONDS))
      val ticks = scheduler.getCompletedTaskCount + 100
      p.beforeWork(task(()))
      val deadline = 10.seconds.fromNow
      while (scheduler.getCompletedTaskCount < ticks && deadline.hasTimeLeft()) Thread.sleep(10)
      val ticked = scheduler.getCompletedTaskCount >= ticks
      val queued = pool(p, "executor").getQueue.size
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
      awaitCallbacks(p)
      p.afterReady(owner)
      p.beforeWork(owner)
      p.afterWork(owner, Left(called))
      p.afterCompleted(owner, Value(()))
      complete(p, owner, called)
      val timed = p.activeTasks(System.nanoTime()).map(_._1)
      val displayed = keys(p)("active")
      val inner = keys(p).values.flatten.filter(Set[Any](owner, called))
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
      awaitCallbacks(p)
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
      awaitCallbacks(p)
      Result
        .assert(thrown.isEmpty && interrupted && retained(p).isEmpty)
        .log(s"thrown=$thrown interrupted=$interrupted retained=${retained(p)}")
    }
  }

  private def interruptedReuse: Result = {
    val entered, release = new CountDownLatch(1)
    using(progress()) { p =>
      val t = task(())
      finish(p, t)
      awaitCallbacks(p)
      val blocker: Runnable = () => {
        entered.countDown()
        release.await()
      }
      Util.ignoreResult(pool(p, "executor").submit(blocker))
      assert(entered.await(10, TimeUnit.SECONDS))
      val results = PMap.empty[Task, TaskResult]
      results(t) = Value(())
      Thread.currentThread().interrupt()
      try p.afterAllCompleted(results)
      finally Util.ignoreResult(Thread.interrupted())
      p.afterReady(t)
      p.beforeWork(t)
      release.countDown()
      awaitCallbacks(p)
      val timed = p.activeTasks(System.nanoTime()).map(_._1)
      p.afterWork(t, Right(Value(())))
      p.afterCompleted(t, Value(()))
      complete(p, t)
      Result
        .assert(timed == Vector(t) && retained(p).isEmpty)
        .log(s"timed=$timed retained=${retained(p)}")
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
        while (waiter.getState != Thread.State.WAITING && deadline.hasTimeLeft()) Thread.sleep(10)
        val waitingBeforeClose = waiter.getState == Thread.State.WAITING
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
}
