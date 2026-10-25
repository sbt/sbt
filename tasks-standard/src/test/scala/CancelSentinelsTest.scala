/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.std

import java.util.concurrent.{ CancellationException, CountDownLatch, Executor, TimeUnit }
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicReference }
import hedgehog.Result
import hedgehog.runner.*
import sbt.{ Completed, ConcurrentRestrictions, Incomplete }
import sbt.std.TaskExtra.*
import scala.collection.mutable
import scala.util.{ Failure, Try }

object CancelSentinelsTest extends Properties:
  override def tests: List[Test] = List(
    example("cancelling sentinels cancels a sentinel that is still being submitted", duringSubmit),
  )

  private val timeoutMillis = 10000L

  private def daemon(threads: mutable.ListBuffer[Thread], name: String)(body: => Unit): Thread =
    val t = new Thread(() => body, name)
    t.setDaemon(true)
    threads.synchronized(threads += t)
    t.start()
    t

  private def awaitState(t: Thread, states: Set[Thread.State]): Boolean =
    val deadline = System.nanoTime + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
    while !states(t.getState) && System.nanoTime < deadline do Thread.sleep(1)
    states(t.getState)

  private def duringSubmit: Result =
    val threads = mutable.ListBuffer.empty[Thread]
    val workerStarted, releaseSubmit = new CountDownLatch(1)
    val pause = new AtomicBoolean(true)
    val interrupted = new AtomicBoolean
    val taken = new AtomicReference[Try[Completed]]
    val executor: Executor = r =>
      daemon(threads, "cancel-sentinels-test-worker")(r.run())
      if pause.getAndSet(false) then
        workerStarted.await(timeoutMillis, TimeUnit.MILLISECONDS)
        releaseSubmit.await(3 * timeoutMillis, TimeUnit.MILLISECONDS)
        ()
    val service = ConcurrentRestrictions.completionService(
      executor,
      ConcurrentRestrictions.tagged(_ => true),
      _ => (),
      _ => true
    )
    val work: () => Completed = () =>
      workerStarted.countDown()
      try Thread.sleep(timeoutMillis)
      catch
        case e: InterruptedException =>
          interrupted.set(true)
          throw e
      throw new IllegalStateException("the sentinel was not cancelled")
    try
      val submitter = daemon(threads, "cancel-sentinels-test-submit"):
        service.submit(task(()), work)
      if !workerStarted.await(timeoutMillis, TimeUnit.MILLISECONDS) then
        Result.failure.log("the sentinel did not start")
      else
        val canceller = daemon(threads, "cancel-sentinels-test-cancel"):
          service.cancelSentinels()
        if !awaitState(canceller, Set(Thread.State.BLOCKED, Thread.State.TERMINATED)) then
          Result.failure.log(s"the cancellation neither waited nor finished: ${canceller.getState}")
        else
          val cancellerWaited = canceller.getState == Thread.State.BLOCKED
          releaseSubmit.countDown()
          submitter.join(timeoutMillis)
          canceller.join(timeoutMillis)
          val taker = daemon(threads, "cancel-sentinels-test-take"):
            taken.set(Try(service.take()))
          taker.join(2 * timeoutMillis)
          val cancelled = Option(taken.get) match
            case Some(Failure(i: Incomplete)) =>
              i.message.contains("cancelled") &&
              i.directCause.exists(_.isInstanceOf[CancellationException])
            case _ => false
          Result
            .assert(!submitter.isAlive && !canceller.isAlive && interrupted.get && cancelled)
            .log(
              s"cancellerWaited=$cancellerWaited submitDone=${!submitter.isAlive} " +
                s"cancelDone=${!canceller.isAlive} interrupted=${interrupted.get} " +
                s"taken=${Option(taken.get).getOrElse("timed out")}"
            )
        end if
      end if
    finally
      releaseSubmit.countDown()
      service.close()
      val started = threads.synchronized(threads.toList)
      started.foreach(_.interrupt())
      started.foreach(_.join(timeoutMillis))
    end try
  end duringSubmit
end CancelSentinelsTest
