/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package testpkg

import java.io.{ InputStream, PrintStream }
import java.util.concurrent.{ LinkedBlockingQueue, TimeUnit, TimeoutException }
import sbt.internal.client.NetworkClient
import sbt.internal.util.Util

/**
 * Regression for https://github.com/sbt/sbt/issues/9095: `reboot` from a client must bring the
 * server back and complete instead of leaving a zombie server that drops the client.
 */
class RebootTest extends AbstractServerTest {
  override val testDirectory: String = "client"

  private object BlockingInputStream extends InputStream {
    override def read(): Int = {
      try Thread.sleep(Long.MaxValue)
      catch { case _: InterruptedException => }
      -1
    }
  }
  private val nullPrintStream = new PrintStream(_ => {}, false)

  private def background[R](f: => R): R = {
    val result = new LinkedBlockingQueue[Either[Throwable, R]]
    val thread = new Thread("reboot-test-client") {
      setDaemon(true)
      override def run(): Unit =
        try Util.ignoreResult(result.put(Right(f)))
        catch { case e: Throwable => Util.ignoreResult(result.put(Left(e))) }
    }
    thread.start()
    result.poll(3, TimeUnit.MINUTES) match {
      case null =>
        thread.interrupt()
        thread.join(10000)
        throw new TimeoutException("client did not complete within 3 minutes")
      case Left(e)  => throw e
      case Right(r) => r
    }
  }

  private def client(args: String*): Int =
    background(
      NetworkClient.client(
        testPath.toFile,
        args.toArray,
        BlockingInputStream,
        nullPrintStream,
        nullPrintStream,
        false
      )
    )

  test("reboot completes and the rebooted server serves the next command") {
    assert(client("reboot") == 0, "reboot from a client must complete with exit 0")
    assert(client("willSucceed") == 0, "the rebooted server must serve a new client connection")
  }
}
