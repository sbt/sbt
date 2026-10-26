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
import java.nio.file.{ Files, Path }
import java.util.concurrent.{ LinkedBlockingQueue, TimeUnit }
import scala.concurrent.duration.*
import scala.util.{ Try, Using }
import sbt.internal.client.NetworkClient
import sbt.protocol.ClientSocket

class WatchSourceTest extends AbstractServerTest:
  override val testDirectory: String = "watch-source"
  private val nullOutput = new PrintStream(_ => {}, false)

  private def awaitFile(path: Path): String =
    val deadline = 10.seconds.fromNow
    var content = ""
    while content.isEmpty && !deadline.isOverdue() do
      if Files.exists(path) then content = new String(Files.readAllBytes(path), UTF_8)
      if content.isEmpty then Thread.sleep(20)
    assert(content.nonEmpty, s"Timed out waiting for $path")
    content

  private def checkCancellation(): Unit =
    val started = testPath.resolve("watch-started")
    val stopped = testPath.resolve("watch-stopped")
    Files.deleteIfExists(started)
    Files.deleteIfExists(stopped)
    val input = new InputStream:
      private var sent = false
      override def read(): Int =
        if sent then
          awaitFile(stopped)
          -1
        else
          awaitFile(started)
          sent = true
          '\n'.toInt
    val result = new LinkedBlockingQueue[Try[Int]]
    val thread = new Thread(() =>
      result.put(Try:
        NetworkClient.client(
          testPath.toFile,
          Array("~marker"),
          input,
          nullOutput,
          nullOutput,
          false
        ))
    )
    thread.setDaemon(true)
    thread.start()
    val exitCode = result.poll(30, TimeUnit.SECONDS)
    assert(exitCode != null, "The watch client did not exit")
    assert(exitCode.get == 0)
    val owner = awaitFile(started)
    assert(owner.startsWith("network-"), s"Unexpected watch owner $owner")
    assert(awaitFile(stopped) == owner, "The termination callback lost the watch's source")
  end checkCancellation

  private def checkDisconnection(mode: String = ""): Unit =
    val started = testPath.resolve("watch-started")
    val stopped = testPath.resolve("watch-stopped")
    Files.deleteIfExists(started)
    Files.deleteIfExists(stopped)
    Files.deleteIfExists(testPath.resolve("watch-followup"))
    Files.deleteIfExists(testPath.resolve("watch-reboot"))
    Files.write(testPath.resolve("watch-mode"), mode.getBytes(UTF_8))
    val (clientSocket, token) =
      ClientSocket.socket(testPath.resolve("project/target/active.json").toFile)
    Using.resource(clientSocket): socket =>
      def send(message: String): Unit =
        val bytes = message.getBytes(UTF_8)
        val output = socket.getOutputStream
        output.write(s"Content-Length: ${bytes.length + 2}\r\n\r\n".getBytes(UTF_8))
        output.write(bytes)
        output.write("\r\n".getBytes(UTF_8))
        output.flush()
      val authentication = token.map(value => s"\"token\":\"$value\",").getOrElse("")
      send(
        s"""{"jsonrpc":"2.0","id":11,"method":"initialize","params":{"initializationOptions":{${authentication}"skipAnalysis":true,"canWork":true}}}"""
      )
      send("""{"jsonrpc":"2.0","id":12,"method":"sbt/exec","params":{"commandLine":"~marker"}}""")
      val owner = awaitFile(started)
      assert(owner.startsWith("network-"), s"Unexpected watch owner $owner")
      socket.close()
      assert(awaitFile(stopped) == owner, "Disconnect cleanup lost the watch's source")
      mode match
        case "queue" =>
          assert(awaitFile(testPath.resolve("watch-followup")) == "none")
        case "reboot" =>
          assert(awaitFile(testPath.resolve("watch-reboot")) == "followup")
        case "reboot-framed" =>
          assert(awaitFile(testPath.resolve("watch-reboot")) == "startServer\nfollowup\nshell")
        case _ =>
  end checkDisconnection

  test("retain the watch owner when a client cancels"):
    checkCancellation()
  test("retain the watch owner when a client disconnects"):
    checkDisconnection()
  test("run a command queued by disconnect cleanup without its old source"):
    checkDisconnection("queue")
  test("reboot from disconnect cleanup without a network result frame"):
    checkDisconnection("reboot")
  test("preserve network reboot handling when a result frame is present"):
    checkDisconnection("reboot-framed")
end WatchSourceTest
