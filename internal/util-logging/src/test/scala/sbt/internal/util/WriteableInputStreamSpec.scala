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
    // an underlying stream that never delivers a byte, so the reader stays parked
    val stream = Terminal.WriteableInputStream(new PipedInputStream(), "test")
    val result = new LinkedBlockingQueue[Integer]
    val reader = new Thread(() => result.put(stream.read()))
    reader.setDaemon(true)
    reader.start()
    // let the reader park inside the buffer take
    Thread.sleep(500)
    stream.close()
    assert(result.poll(5, TimeUnit.SECONDS) == -1)
    assert(stream.read() == -1)
end WriteableInputStreamSpec
