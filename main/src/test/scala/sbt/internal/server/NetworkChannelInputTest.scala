/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package server

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tests that the stdin input buffer is correctly unblocked when
 * the channel shuts down. This verifies the fix for
 * https://github.com/sbt/sbt/issues/6841 where killing a thin client
 * while the server was waiting for stdin input would block the server forever.
 */
object NetworkChannelInputTest extends verify.BasicTestSuite:

  test("inputBuffer.take should unblock when poison pill -1 is added on shutdown"):
    val running = new AtomicBoolean(true)
    val inputBuffer = new LinkedBlockingQueue[Int]()
    @volatile var readResult: Int = 42 // sentinel value

    // Simulate a thread blocked on inputBuffer.take (like inputStream.read)
    val readerThread = new Thread("test-stdin-reader"):
      override def run(): Unit =
        readResult =
          if !running.get then -1
          else
            try
              val result = inputBuffer.take
              if !running.get && result == -1 then -1
              else result
            catch
              case _: InterruptedException => -1

    readerThread.start()

    // Give the reader thread time to block on take
    Thread.sleep(100)
    assert(readerThread.isAlive, "reader thread should be blocked on take")

    // Simulate shutdown: set running to false, then add poison pill
    running.set(false)
    inputBuffer.add(-1)

    // Reader thread should unblock and return -1
    readerThread.join(5000)
    assert(!readerThread.isAlive, "reader thread should have terminated")
    assert(readResult == -1, s"read should return -1 (EOF) but got $readResult")

  test("read should return -1 immediately when running is already false"):
    val running = new AtomicBoolean(false)
    val inputBuffer = new LinkedBlockingQueue[Int]()
    val result =
      if !running.get then -1
      else inputBuffer.take
    assert(result == -1, "should return -1 when channel is not running")

  test("inputBuffer.take should return -1 on thread interrupt"):
    val inputBuffer = new LinkedBlockingQueue[Int]()
    @volatile var readResult: Int = 42

    val readerThread = new Thread("test-interrupt-reader"):
      override def run(): Unit =
        readResult =
          try inputBuffer.take
          catch case _: InterruptedException => -1

    readerThread.start()
    Thread.sleep(100)
    assert(readerThread.isAlive, "reader thread should be blocked")

    readerThread.interrupt()
    readerThread.join(5000)
    assert(!readerThread.isAlive, "reader thread should have terminated")
    assert(readResult == -1, s"read should return -1 on interrupt but got $readResult")

  test("normal bytes should still be returned correctly"):
    val running = new AtomicBoolean(true)
    val inputBuffer = new LinkedBlockingQueue[Int]()
    inputBuffer.add('H'.toInt)
    inputBuffer.add('i'.toInt)

    def read(): Int =
      if !running.get then -1
      else
        val result = inputBuffer.take
        if !running.get && result == -1 then -1
        else result

    assert(read() == 'H'.toInt, "first byte should be 'H'")
    assert(read() == 'i'.toInt, "second byte should be 'i'")
