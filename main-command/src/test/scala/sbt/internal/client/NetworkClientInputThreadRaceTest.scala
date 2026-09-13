/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.client

import java.io.{ ByteArrayOutputStream, InputStream, PrintStream }
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{ ExecutorService, Executors, TimeUnit }
import sbt.util.Level
import scala.util.Using
import verify.BasicTestSuite

/**
 * Regression test for #9507: a `readSystemIn` request arriving while the previous reader thread
 * was silently dropped mid-teardown, and nothing else would ever ask for
 * that byte again. This resulted in the session stopping to accepting input.
 */
object NetworkClientInputThreadRaceTest extends BasicTestSuite:

  final val chainLength = 40
  final val perSessionTimeoutMillis = 2000L

  val dummyConsole = new ConsoleInterface:
    def appendLog(level: Level.Value, message: => String): Unit = ()
    def success(msg: String): Unit = ()

  val nullPrintStream = new PrintStream(new ByteArrayOutputStream())

  def newClient(in: InputStream): NetworkClient =
    new NetworkClient(
      NetworkClient.parseArgs(Array("compile")),
      dummyConsole,
      in,
      nullPrintStream,
      nullPrintStream,
      false,
    )

  test("startInputThread should not drop a readSystemIn request under contention"):
    val numSessions = 50
    withFakeLoad:
      val wedged = (1 to numSessions).map(_ => session).sum
      assert(wedged == 0, s"$wedged/$numSessions sessions permanently wedged (expected 0)")

  /**
   * reads succeed instantly (as if from an already-filled paste buffer)
   */
  class ChainedInputStream extends InputStream:
    val reads = new AtomicInteger(0)
    @volatile var onRead: Int => Unit = _ => ()
    override def read(): Int =
      val n = reads.incrementAndGet()
      onRead(n)
      'a'.toInt

  def session: Int =
    Using.resource(new ChainedInputStream): in =>
      Using.resource(newClient(in)): client =>
        val dispatcher: ExecutorService = Executors.newSingleThreadExecutor()
        def requestNext(): Unit =
          dispatcher.submit((() => client.startInputThread()): Runnable): Unit
        in.onRead = n => if n < chainLength then requestNext()
        try
          requestNext()
          val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(perSessionTimeoutMillis)
          while in.reads.get() < chainLength && System.nanoTime() < deadline do Thread.sleep(1)
          // the session is wedged
          if in.reads.get() < chainLength then 1
          else 0
        finally
          dispatcher.shutdownNow()
          client.close()

  /*
   * Tests f under a busy spin to emulate contention/jitter.
   */
  def withFakeLoad[A1](f: => A1): A1 =
    val fakeLoad = (1 to Runtime.getRuntime.availableProcessors).toList.map: _ =>
      val busyThread = new Thread(() =>
        var x = 0L
        while !Thread.currentThread.isInterrupted do x += System.nanoTime()
      )
      busyThread.setDaemon(true)
      busyThread.start()
      busyThread
    try
      f
    finally fakeLoad.foreach(_.interrupt())
end NetworkClientInputThreadRaceTest
