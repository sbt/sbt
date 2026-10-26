/*
 * sbt
 * Copyright 2026, Scala center
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.util.concurrent.{ CountDownLatch, TimeUnit }
import java.util.concurrent.atomic.{ AtomicInteger, AtomicReference }
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

  private def withEngines(engines: Engine*)(test: => Result): Result = synchronized:
    try test
    finally engines.foreach(EvaluateTask.removeRunningTaskEngine)

  private def register(engine: Engine, log: Logger = Logger.Null): () => Unit =
    EvaluateTask.addRunningTaskEngine(engine, log)

  private def start(cancel: () => Unit, error: AtomicReference[Throwable]): Thread =
    val thread = new Thread(() =>
      try cancel()
      catch case e: Throwable => error.set(e)
    )
    thread.start()
    thread

  private def await(latch: CountDownLatch): Unit = assert(latch.await(10, TimeUnit.SECONDS))

  override def tests: List[Test] = List(
    example("remove engines by identity and cancel overlapping evaluations outer first", {
      val order = new java.util.concurrent.ConcurrentLinkedQueue[Int]()
      val engines = List.tabulate(4)(i => new Engine(() => { order.add(i); () }))
      withEngines(engines*):
        val cancel = register(engines.head)
        engines.tail.foreach(register(_))
        EvaluateTask.removeRunningTaskEngine(engines(1))
        EvaluateTask.removeRunningTaskEngine(engines(2))
        cancel()
        Result.assert(
          engines.map(_.cancellations.get()) == List(1, 0, 0, 1) &&
            order.toArray.toList == List(0, 3)
        )
    }),
    example("retain the outer engine after the inner evaluation finishes", {
      val outer = new Engine
      val inner = new Engine
      withEngines(outer, inner):
        register(outer)
        register(inner)
        EvaluateTask.removeRunningTaskEngine(inner)
        EvaluateTask.runningTaskCancellation.get.apply()
        EvaluateTask.removeRunningTaskEngine(outer)
        Result.assert(
          outer.cancellations.get() == 1 && inner.cancellations.get() == 0 &&
            EvaluateTask.runningTaskCancellation.isEmpty
        )
    }),
    example("repeat cancellation and common cleanup once per request using the root logger", {
      val outer = new Engine
      val inner = new Engine
      val rootLog = new Log
      val innerLog = new Log
      withEngines(outer, inner):
        val cancel = register(outer, rootLog)
        register(inner, innerLog)
        cancel()
        cancel()
        Result.assert(
          outer.cancellations.get() == 2 && inner.cancellations.get() == 2 &&
            rootLog.warnings.get() == 2 && innerLog.warnings.get() == 0
        )
    }),
    example("stop a new registration racing cancellation while the original remains live", {
      val entered = new CountDownLatch(1)
      val release = new CountDownLatch(1)
      val registered = new CountDownLatch(1)
      val outer = new Engine(() => { entered.countDown(); await(release) })
      val inner = new Engine
      val log = new Log
      val error = new AtomicReference[Throwable]()
      withEngines(outer, inner):
        val cancel = register(outer, log)
        val cancellation = start(cancel, error)
        var registration: Thread = null
        var raced = false
        try
          await(entered)
          registration = start(() => { register(inner); registered.countDown() }, error)
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
    }),
    example("retired callbacks cannot cancel a later execution", {
      val first = new Engine
      val next = new Engine
      withEngines(first, next):
        val old = register(first)
        val oldNetwork = EvaluateTask.runningTaskCancellation.get
        EvaluateTask.removeRunningTaskEngine(first)
        val current = register(next)
        old()
        oldNetwork()
        val untouched = next.cancellations.get() == 0
        current()
        Result.assert(untouched && next.cancellations.get() == 1)
    }),
    example("an old cancellation finally cannot reset cancellation of a new generation", {
      val oldEntered = new CountDownLatch(1)
      val oldRelease = new CountDownLatch(1)
      val newEntered = new CountDownLatch(1)
      val newRelease = new CountDownLatch(1)
      val first = new Engine(() => { oldEntered.countDown(); await(oldRelease) })
      val next = new Engine(() => { newEntered.countDown(); await(newRelease) })
      val last = new Engine
      val log = new Log
      val error = new AtomicReference[Throwable]()
      withEngines(first, next, last):
        val old = start(register(first), error)
        var current: Thread = null
        var isolated = false
        try
          await(oldEntered)
          EvaluateTask.removeRunningTaskEngine(first)
          val cancel = register(next, log)
          current = start(cancel, error)
          await(newEntered)
          oldRelease.countDown()
          old.join(10000)
          cancel()
          register(last)
          isolated = log.warnings.get() == 1 && next.cancellations.get() == 1 &&
            last.cancellations.get() == 1
        finally
          oldRelease.countDown()
          newRelease.countDown()
          old.join(10000)
          if current != null then current.join(10000)
        Result.assert(isolated && !old.isAlive() && !current.isAlive() && error.get() == null)
    }),
    example("attempt every engine despite cleanup and engine failures and allow a retry", {
      val first = new Engine(() => sys.error("engine failure"))
      val next = new Engine
      val log = new Log(() => sys.error("cleanup failure"))
      withEngines(first, next):
        val cancel = register(first, log)
        register(next)
        val initial = scala.util.Try(cancel())
        val retry = scala.util.Try(cancel())
        Result.assert(
          initial.failed.get.getMessage == "cleanup failure" && retry.isFailure &&
            first.cancellations.get() == 2 && next.cancellations.get() == 2 &&
            log.warnings.get() == 2
        )
    }),
    example("remove a failed registration after cancellation", {
      val outer = new Engine
      val inner = new Engine(() => sys.error("registration failure"))
      withEngines(outer, inner):
        val cancel = register(outer)
        cancel()
        val result = scala.util.Try(register(inner))
        cancel()
        Result.assert(
          result.failed.get.getMessage == "registration failure" &&
            outer.cancellations.get() == 2 && inner.cancellations.get() == 1
        )
    }),
    example("custom cancellation strategies receive the callback for all active engines", {
      val outer = new Engine
      val inner = new Engine
      val finished = new AtomicInteger()
      val strategy = new TaskCancellationStrategy:
        type State = () => Unit
        def onTaskEngineStart(cancel: () => Unit): State = cancel
        def onTaskEngineFinish(state: State): Unit =
          finished.incrementAndGet()
          ()
      withEngines(outer, inner):
        val cancel = register(outer)
        register(inner)
        TaskCancellationStrategy.Null.onTaskEngineStart(cancel)
        val untouched = outer.cancellations.get() == 0 && inner.cancellations.get() == 0
        val state = strategy.onTaskEngineStart(cancel)
        try state()
        finally strategy.onTaskEngineFinish(state)
        Result.assert(
          untouched && outer.cancellations.get() == 1 && inner.cancellations.get() == 1 &&
            finished.get() == 1
        )
    })
  )
end TaskCancellationTest
