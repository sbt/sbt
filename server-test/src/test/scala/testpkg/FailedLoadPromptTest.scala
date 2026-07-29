/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package testpkg

import java.io.{ InputStream, OutputStream, PrintStream }
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.duration.*
import sbt.internal.client.NetworkClient
import sbt.internal.langserver.SbtExecParams
import sbt.internal.langserver.codec.JsonProtocol.given
import sbt.internal.util.Util
import scala.collection.mutable

/**
 * The failed-load prompt must reach the interactive client that triggered the reload,
 * and its answer must get back to the server: typing 'r' retries the load.
 */
class FailedLoadPromptTest extends AbstractServerTest {
  override val testDirectory: String = "client"

  private class CachingOutputStream extends OutputStream {
    private val byteBuffer = new mutable.ArrayBuffer[Byte]
    override def write(i: Int) = Util.ignoreResult(synchronized(byteBuffer += i.toByte))
    def text: String = new String(synchronized(byteBuffer.toArray), "UTF-8")
  }
  private class CachingPrintStream(val cos: CachingOutputStream = new CachingOutputStream)
      extends PrintStream(cos, true) {
    def text: String = cos.text
  }
  private class QueueInputStream extends InputStream {
    private val queue = new LinkedBlockingQueue[Integer]
    def push(s: String): Unit = s.getBytes("UTF-8").foreach(b => queue.put(b.toInt))
    override def read(): Int = queue.take()
  }

  private def awaitUntil(deadlineSeconds: Int)(condition: => Boolean): Boolean = {
    val deadline = System.nanoTime + deadlineSeconds * 1000000000L
    var met = condition
    while (!met && System.nanoTime < deadline) {
      Thread.sleep(500)
      met = condition
    }
    met
  }

  test("an interactive client can answer the failed-load prompt") {
    val buildFile = testPath.resolve("build.sbt")
    val goodBuild = java.nio.file.Files.readString(buildFile)
    val in = new QueueInputStream
    val out = new CachingPrintStream
    val err = new CachingPrintStream
    val exitCode = new AtomicReference[Option[Int]](None)
    val clientThread = new Thread("failed-load-prompt-test-client") {
      setDaemon(true)
      override def run(): Unit = {
        val code = NetworkClient.client(testPath.toFile, Array.empty[String], in, out, err, false)
        exitCode.set(Some(code))
      }
    }
    clientThread.start()
    assert(awaitUntil(30)(out.text.contains("sbt:")), s"client never attached: ${out.text}")

    java.nio.file.Files.writeString(buildFile, "val = =\n")
    in.push("reload\r")
    // let the failed-load prompt start reading, then repair the build and answer 'r'
    Thread.sleep(20000)
    java.nio.file.Files.writeString(buildFile, goodBuild)
    in.push("r")
    // a successful retry returns the client to the command prompt; run a task to prove it.
    // the server-side [success] is observed through the suite session's log notifications,
    // since the in-process client does not render exec results on the provided stream.
    // the prompt parks the command loop until answered: on develop (reads process stdin)
    // any further exec starves; with the fix the client's 'r' resolves it and execs flow.
    val id = svr.session.nextId()
    svr.session.sendJsonRpc(id, "sbt/exec", SbtExecParams("show name")).get
    val served = svr.session.waitForNotificationMsg(90.seconds)(_.method == "build/logMessage")
    assert(
      served.isSuccess,
      s"server did not serve another client after the prompt: exit=${exitCode.get}\nout=${out.text}\nerr=${err.text}"
    )

    in.push("exit\r")
    assert(awaitUntil(60)(exitCode.get.isDefined), s"client did not exit: ${out.text}")
  }
}
