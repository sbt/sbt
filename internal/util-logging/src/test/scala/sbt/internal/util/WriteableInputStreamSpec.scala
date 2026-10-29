/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import java.io.PipedInputStream
import java.util.concurrent.{ LinkedBlockingQueue, TimeUnit }
import verify.BasicTestSuite

object WriteableInputStreamSpec extends BasicTestSuite:
  test("close wakes a parked reader with EOF"):
    val (stream, result) = parkedReader()
    stream.close()
    assert(result.poll(5, TimeUnit.SECONDS) == -1)
    assert(stream.read() == -1)

  test("close on an interrupted thread keeps the interrupt and wakes a parked reader"):
    val (stream, result) = parkedReader()
    val (thrown, stillInterrupted) =
      try
        Thread.currentThread().interrupt()
        val thrown =
          try
            stream.close()
            None
          catch case e: InterruptedException => Some(e)
        (thrown, Thread.interrupted())
      finally Util.ignoreResult(Thread.interrupted())
    assert(thrown.isEmpty, s"expected no exception, but got ${thrown.orNull}")
    assert(stillInterrupted, "expected the interrupt flag to be kept, but it was cleared")
    assert(result.poll(5, TimeUnit.SECONDS) == -1)

  /** A stream whose input never delivers a byte, and a reader parked in its buffer. */
  private def parkedReader(): (Terminal.WriteableInputStream, LinkedBlockingQueue[Integer]) =
    val stream = Terminal.WriteableInputStream(new PipedInputStream(), "test")
    val result = new LinkedBlockingQueue[Integer]
    val reader = new Thread(() => result.put(stream.read()))
    reader.setDaemon(true)
    reader.start()
    Thread.sleep(500)
    (stream, result)
end WriteableInputStreamSpec
