/*
 * sbt
 * Copyright 2026, Scala center
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.util.concurrent.{ CountDownLatch, TimeUnit }
import java.util.concurrent.atomic.{ AtomicInteger, AtomicReference }
import sbt.internal.TaskEngineTracker
import sbt.util.{ Level, Logger }
import hedgehog.*
import hedgehog.runner.*

object TaskCancellationTest extends Properties:
  private class Engine(cancel: () => Unit = () => ()) extends RunningTaskEngine:
    val cancellations = new AtomicInteger()
    def cancelAndShutdown(): Unit =
      cancellations.incrementAndGet()
      cancel()
    override def equals(other: Any): Boolean = other.isInstanceOf[Engine]
    override def hashCode(): Int = 0
  end Engine

  private class Log(write: () => Unit = () => ()) extends Logger:
    val warnings = new AtomicInteger()
    def trace(t: => Throwable): Unit = ()
    def success(message: => String): Unit = ()
    def log(level: Level.Value, message: => String): Unit =
      if level == Level.Warn then
        warnings.incrementAndGet()
        write()
  end Log

  private def withEngines(engines: Engine*)(test: TaskEngineTracker => Result): Result =
    val tracker = new TaskEngineTracker(_.warn("Canceling execution..."))
    try test(tracker)
    finally engines.foreach(tracker.remove)

  private def check(name: String)(test: => Result): Test = example(name, test)

  private def start(cancel: () => Unit, error: AtomicReference[Throwable]): Thread =
    val thread = new Thread(() =>
      try cancel()
      catch case e: Throwable => error.set(e)
    )
    thread.start()
    thread

  private def await(latch: CountDownLatch): Unit = assert(latch.await(10, TimeUnit.SECONDS))

  override def tests: List[Test] = List(
    check("remove engines by identity and cancel overlapping evaluations outer first"):
      val order = new java.util.concurrent.ConcurrentLinkedQueue[Int]()
      val engines = List.tabulate(4)(i =>
        new Engine(() =>
          order.add(i); ()
        )
      )
      withEngines(engines*): tracker =>
        val cancel = tracker.add(engines.head)
        engines.tail.foreach(tracker.add(_))
        tracker.remove(engines(1))
        tracker.remove(engines(2))
        cancel()
        Result.assert(
          engines.map(_.cancellations.get()) == List(1, 0, 0, 1) &&
            order.toArray.toList == List(0, 3)
        )
    ,
    check("retain the outer engine after the inner evaluation finishes"):
      val outer = new Engine
      val inner = new Engine
      withEngines(outer, inner): tracker =>
        tracker.add(outer)
        tracker.add(inner)
        tracker.remove(inner)
        tracker.currentCancellation.get.apply()
        tracker.remove(outer)
        Result.assert(
          outer.cancellations.get() == 1 && inner.cancellations.get() == 0 &&
            tracker.currentCancellation.isEmpty
        )
    ,
    check("repeat cancellation and common cleanup once per request using the root logger"):
      val outer = new Engine
      val inner = new Engine
      val rootLog = new Log
      val innerLog = new Log
      withEngines(outer, inner): tracker =>
        val cancel = tracker.add(outer, rootLog)
        tracker.add(inner, innerLog)
        cancel()
        cancel()
        Result.assert(
          outer.cancellations.get() == 2 && inner.cancellations.get() == 2 &&
            rootLog.warnings.get() == 2 && innerLog.warnings.get() == 0
        )
    ,
    check("stop a new registration racing cancellation while the original remains live"):
      val entered = new CountDownLatch(1)
      val release = new CountDownLatch(1)
      val registered = new CountDownLatch(1)
      val outer = new Engine(() =>
        entered.countDown(); await(release)
      )
      val inner = new Engine
      val log = new Log
      val error = new AtomicReference[Throwable]()
      withEngines(outer, inner): tracker =>
        val cancel = tracker.add(outer, log)
        val cancellation = start(cancel, error)
        var registration: Thread = null
        var raced = false
        try
          await(entered)
          registration = start(
            () =>
              tracker.add(inner); registered.countDown()
            ,
            error
          )
          raced = registered.await(2, TimeUnit.SECONDS)
          cancel()
        finally
          release.countDown()
          cancellation.join(10000)
          if registration != null then registration.join(10000)
        Result.assert(
          raced && !cancellation.isAlive() && !registration.isAlive() && error.get() == null &&
            outer.cancellations.get() == 1 && inner.cancellations.get() == 1 &&
            log.warnings.get() == 1
        )
    ,
    check("retired callbacks cannot cancel a later execution"):
      val first = new Engine
      val next = new Engine
      withEngines(first, next): tracker =>
        val old = tracker.add(first)
        val oldNetwork = tracker.currentCancellation.get
        tracker.remove(first)
        val current = tracker.add(next)
        old()
        oldNetwork()
        val untouched = next.cancellations.get() == 0
        current()
        Result.assert(untouched && next.cancellations.get() == 1)
    ,
    check("an old cancellation finally cannot reset cancellation of a new generation"):
      val oldEntered = new CountDownLatch(1)
      val oldRelease = new CountDownLatch(1)
      val newEntered = new CountDownLatch(1)
      val newRelease = new CountDownLatch(1)
      val first = new Engine(() =>
        oldEntered.countDown(); await(oldRelease)
      )
      val next = new Engine(() =>
        newEntered.countDown(); await(newRelease)
      )
      val last = new Engine
      val log = new Log
      val error = new AtomicReference[Throwable]()
      withEngines(first, next, last): tracker =>
        val old = start(tracker.add(first), error)
        var current: Thread = null
        var isolated = false
        try
          await(oldEntered)
          tracker.remove(first)
          val cancel = tracker.add(next, log)
          current = start(cancel, error)
          await(newEntered)
          oldRelease.countDown()
          old.join(10000)
          cancel()
          tracker.add(last)
          isolated = log.warnings.get() == 1 && next.cancellations.get() == 1 &&
            last.cancellations.get() == 1
        finally
          oldRelease.countDown()
          newRelease.countDown()
          old.join(10000)
          if current != null then current.join(10000)
        Result.assert(isolated && !old.isAlive() && !current.isAlive() && error.get() == null)
    ,
    check("attempt every engine despite cleanup and engine failures and allow a retry"):
      val first = new Engine(() => sys.error("engine failure"))
      val next = new Engine
      val log = new Log(() => sys.error("cleanup failure"))
      withEngines(first, next): tracker =>
        val cancel = tracker.add(first, log)
        tracker.add(next)
        val initial = scala.util.Try(cancel())
        val retry = scala.util.Try(cancel())
        Result.assert(
          initial.failed.get.getMessage == "cleanup failure" && retry.isFailure &&
            first.cancellations.get() == 2 && next.cancellations.get() == 2 &&
            log.warnings.get() == 2
        )
    ,
    check("remove a failed registration after cancellation"):
      val outer = new Engine
      val inner = new Engine(() => sys.error("registration failure"))
      withEngines(outer, inner): tracker =>
        val cancel = tracker.add(outer)
        cancel()
        val result = scala.util.Try(tracker.add(inner))
        cancel()
        Result.assert(
          result.failed.get.getMessage == "registration failure" &&
            outer.cancellations.get() == 2 && inner.cancellations.get() == 1
        )
    ,
    check("cancellation is independent between trackers"):
      val first = new Engine
      val next = new Engine
      withEngines(first): firstTracker =>
        withEngines(next): nextTracker =>
          val cancel = firstTracker.add(first)
          nextTracker.add(next)
          cancel()
          Result.assert(first.cancellations.get() == 1 && next.cancellations.get() == 0)
    ,
    check("custom cancellation strategies receive the callback for all active engines"):
      val outer = new Engine
      val inner = new Engine
      val finished = new AtomicInteger()
      val strategy = new TaskCancellationStrategy:
        type State = () => Unit
        def onTaskEngineStart(cancel: () => Unit): State = cancel
        def onTaskEngineFinish(state: State): Unit =
          finished.incrementAndGet()
          ()
      withEngines(outer, inner): tracker =>
        val cancel = tracker.add(outer)
        tracker.add(inner)
        TaskCancellationStrategy.Null.onTaskEngineStart(cancel)
        val untouched = outer.cancellations.get() == 0 && inner.cancellations.get() == 0
        val state = strategy.onTaskEngineStart(cancel)
        try state()
        finally strategy.onTaskEngineFinish(state)
        Result.assert(
          untouched && outer.cancellations.get() == 1 && inner.cancellations.get() == 1 &&
            finished.get() == 1
        )
  )
end TaskCancellationTest
