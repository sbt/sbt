/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package testpkg

import java.io.{ InputStream, PrintStream }
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.util.concurrent.{ LinkedBlockingQueue, TimeUnit }
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration._
import scala.util.Try
import sbt.internal.client.NetworkClient

object ReloadFailureTest extends AbstractServerTest {
  override val testDirectory: String = "reload-failure"
  private val requestId = new AtomicInteger(100)

  private object NullInputStream extends InputStream {
    override def read(): Int = {
      try this.synchronized(this.wait())
      catch { case _: InterruptedException => }
      -1
    }
  }

  private def runBatchClient(commands: String*): Int = {
    val output = new PrintStream(_ => {}, false)
    val result = new LinkedBlockingQueue[Try[Int]]
    val thread = new Thread(
      () =>
        result.put(Try {
          NetworkClient.client(
            testPath.toFile,
            commands.toArray,
            NullInputStream,
            output,
            output,
            false
          )
        })
    )
    thread.setDaemon(true)
    thread.start()
    try {
      val completed = result.poll(60, TimeUnit.SECONDS)
      assert(completed != null, s"Batch client did not finish: ${commands.mkString("; ")}")
      completed.get
    } finally {
      thread.interrupt()
      output.close()
    }
  }

  private def reload(): Boolean = {
    val id = requestId.incrementAndGet()
    svr.sendJsonRpc(s"""{"jsonrpc":"2.0","id":"$id","method":"workspace/reload"}""")
    !svr.waitForResponse(30.seconds, id).contains("\"error\"")
  }

  private def readInput(value: String): Unit = {
    Files.write(testPath.resolve("input"), value.getBytes(UTF_8))
    assert(runBatchClient("readInput") == 0, s"readInput failed for $value")
    val read = new String(Files.readAllBytes(testPath.resolve("read-input")), UTF_8)
    assert(read == value)
  }

  test("keep the file tree repository open when a build load hook fails") { _ =>
    readInput("before")
    Files.write(testPath.resolve("fail-load"), Array.emptyByteArray)
    assert(!reload(), "The throwing load hook did not fail the reload")
    readInput("restored")
    assert(reload(), "The next reload failed")
    readInput("reloaded")
  }
}
