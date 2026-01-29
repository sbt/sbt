/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package testpkg

import java.io.IOException
import java.net.Socket
import java.util.concurrent.{ LinkedBlockingQueue, TimeUnit }
import java.util.concurrent.atomic.AtomicBoolean
import sbt.protocol.ClientSocket

import scala.annotation.tailrec
import scala.concurrent.duration.*

class ChannelCursorTest extends AbstractServerTest {
  override val testDirectory: String = "channel-cursor"

  private def createSecondConnection()
      : (Socket, java.io.OutputStream, LinkedBlockingQueue[String], AtomicBoolean) = {
    val portfile = testPath.resolve("project/target/active.json").toFile
    @tailrec
    def connect(attempt: Int): Socket = {
      val res =
        try Some(ClientSocket.socket(portfile)._1)
        catch { case _: IOException if attempt < 10 => None }
      res match {
        case Some(s) => s
        case _ =>
          Thread.sleep(100)
          connect(attempt + 1)
      }
    }
    val sk = connect(0)
    val out = sk.getOutputStream
    val in = sk.getInputStream
    val lines = new LinkedBlockingQueue[String]
    val running = new AtomicBoolean(true)
    new Thread(
      () => {
        while (running.get) {
          try lines.put(sbt.ReadJson(in, running))
          catch { case _: Exception => running.set(false) }
        }
      },
      "sbt-server-test-read-thread-2"
    ) {
      setDaemon(true)
      start()
    }
    (sk, out, lines, running)
  }

  private def sendJsonRpc(out: java.io.OutputStream, message: String): Unit = {
    def writeLine(s: String): Unit = {
      val retByte: Byte = '\r'.toByte
      val delimiter: Byte = '\n'.toByte
      if (s != "") {
        out.write(s.getBytes("UTF-8"))
      }
      out.write(retByte.toInt)
      out.write(delimiter.toInt)
      out.flush
    }
    writeLine(s"""Content-Length: ${message.size + 2}""")
    writeLine("")
    writeLine(message)
  }

  private def waitForString(lines: LinkedBlockingQueue[String], duration: FiniteDuration)(
      f: String => Boolean
  ): Boolean = {
    val deadline = duration.fromNow
    @tailrec def impl(): Boolean =
      lines.poll(deadline.timeLeft.toMillis, TimeUnit.MILLISECONDS) match {
        case null => false
        case s    => if (!f(s) && !deadline.isOverdue) impl() else !deadline.isOverdue()
      }
    impl()
  }

  test("channel cursor - independent project cursors") {
    val (sk2, out2, lines2, running2) = createSecondConnection()
    try {
      sendJsonRpc(
        out2,
        """{ "jsonrpc": "2.0", "id": 1, "method": "initialize", "params": { "initializationOptions": { "skipAnalysis": true } } }"""
      )
      waitForString(lines2, 10.seconds)(_.contains(""""capabilities":{"""))

      svr.sendJsonRpc(
        """{ "jsonrpc": "2.0", "id": 10, "method": "sbt/exec", "params": { "commandLine": "project projectA" } }"""
      )
      assert(
        svr.waitForString(10.seconds) { s =>
          println(s"[channel1] $s")
          s.contains("projectA") || s.contains("\"execId\":10")
        },
        "Channel 1 should switch to projectA"
      )

      sendJsonRpc(
        out2,
        """{ "jsonrpc": "2.0", "id": 20, "method": "sbt/exec", "params": { "commandLine": "project projectB" } }"""
      )
      assert(
        waitForString(lines2, 10.seconds) { s =>
          println(s"[channel2] $s")
          s.contains("projectB") || s.contains("\"execId\":20")
        },
        "Channel 2 should switch to projectB"
      )

      svr.sendJsonRpc(
        """{ "jsonrpc": "2.0", "id": 11, "method": "sbt/exec", "params": { "commandLine": "printCurrentProject" } }"""
      )
      var foundProjectA = false
      assert(
        svr.waitForString(30.seconds) { s =>
          println(s"[channel1 name] $s")
          if (s.contains("CURRENT_PROJECT_IS:project-a")) foundProjectA = true
          s.contains("\"execId\":11") && s.contains("\"status\":\"Done\"")
        },
        "First channel printCurrentProject command should complete"
      )
      assert(foundProjectA, "First channel should still be on projectA")

      sendJsonRpc(
        out2,
        """{ "jsonrpc": "2.0", "id": 21, "method": "sbt/exec", "params": { "commandLine": "printCurrentProject" } }"""
      )
      var foundProjectB = false
      assert(
        waitForString(lines2, 30.seconds) { s =>
          println(s"[channel2 name] $s")
          if (s.contains("CURRENT_PROJECT_IS:project-b")) foundProjectB = true
          s.contains("\"execId\":21") && s.contains("\"status\":\"Done\"")
        },
        "Second channel printCurrentProject command should complete"
      )
      assert(foundProjectB, "Second channel should still be on projectB")
    } finally {
      running2.set(false)
      sk2.close()
    }
  }
}
