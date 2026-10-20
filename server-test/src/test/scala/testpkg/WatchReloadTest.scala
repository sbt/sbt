/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package testpkg

import java.io.{ ByteArrayOutputStream, InputStream, PrintStream }
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.util.concurrent.{ CountDownLatch, LinkedBlockingQueue, TimeUnit }
import java.util.concurrent.atomic.{ AtomicInteger, AtomicReference }
import scala.concurrent.duration._
import scala.util.{ Try, Using }
import sbt.internal.client.NetworkClient

object WatchReloadTest extends AbstractServerTest {
  override val testDirectory = "watch-reload"
  private lazy val buildFile = testPath.resolve("build.sbt")
  private lazy val originalBuild = new String(Files.readAllBytes(buildFile), UTF_8)
  private val requestId = new AtomicInteger(100)
  private val controls = List(
    "build-name",
    "automatic-reload",
    "queue-callback",
    "fail-termination",
    "fail-unload",
    "fail-load",
    "callback-queued",
    "iteration-marker",
    "iteration-other",
  )

  private def write(name: String, value: String = ""): Unit = {
    Files.write(testPath.resolve(name), value.getBytes(UTF_8))
    ()
  }

  private def read(name: String): String = {
    val path = testPath.resolve(name)
    if (Files.exists(path)) new String(Files.readAllBytes(path), UTF_8) else ""
  }

  private def waitUntil(timeout: FiniteDuration)(p: => Boolean): Boolean = {
    val deadline = timeout.fromNow
    while (!p && deadline.hasTimeLeft()) Thread.sleep(100)
    p
  }

  private def awaitText(name: String)(p: String => Boolean): String = {
    assert(waitUntil(20.seconds)(p(read(name))), s"Timed out waiting for $name: ${read(name)}")
    read(name)
  }

  private def stops(command: String): List[String] =
    read("events").linesIterator.filter(_.startsWith(s"stop:$command:")).toList

  override def setup(): Unit = {
    super.setup()
    controls.foreach(name => Files.deleteIfExists(testPath.resolve(name)))
    write("build.sbt", originalBuild)
    write("input", "v0")
    assert(runBatchClient("reload") == 0)
    write("events")
  }

  private class QueueInput extends InputStream {
    private val bytes = new LinkedBlockingQueue[Integer]
    def push(text: String): Unit = text.getBytes(UTF_8).foreach(b => bytes.put(b.toInt))
    override def read(): Int = bytes.take()
  }

  private class WatchClient(command: String = "~marker", interactive: Boolean = false)
      extends AutoCloseable {
    private val label = if (command.endsWith("other")) "other" else "marker"
    Files.deleteIfExists(testPath.resolve(s"iteration-$label"))
    private val input = new QueueInput
    private val buffer = new ByteArrayOutputStream
    private val output = new PrintStream(buffer, true, "UTF-8")
    private val finished = new CountDownLatch(1)
    private val result = new AtomicReference[Try[Int]]
    private val thread = new Thread(() => {
      result.set(Try {
        val args = if (interactive) Array.empty[String] else Array(command)
        NetworkClient.client(testPath.toFile, args, input, output, output, false)
      })
      finished.countDown()
    })
    thread.setDaemon(true)
    thread.start()
    if (interactive) send(command)

    def send(command: String): Unit = input.push(command + "\r")
    def text: String = buffer.toString("UTF-8")
    def isDone: Boolean = finished.getCount == 0
    def awaitExit(): Int = {
      assert(finished.await(20, TimeUnit.SECONDS), s"Watch client did not finish: $text")
      result.get().get
    }

    override def close(): Unit = {
      if (!isDone) input.push("\n")
      if (interactive) send("exit")
      if (!finished.await(20, TimeUnit.SECONDS)) thread.interrupt()
      output.close()
    }
  }

  private def runBatchClient(commands: String*): Int = {
    val input = new QueueInput
    val output = new PrintStream(_ => {}, false)
    val result = new LinkedBlockingQueue[Try[Int]]
    val thread = new Thread(
      () =>
        result.put(Try {
          NetworkClient.client(testPath.toFile, commands.toArray, input, output, output, false)
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

  private def owner(command: String = "marker"): String =
    awaitText(s"iteration-$command")(_.startsWith("network-")).takeWhile(_ != ':')

  private def bspReload(expectSuccess: Boolean): Unit = {
    val id = requestId.incrementAndGet()
    svr.sendJsonRpc(s"""{"jsonrpc":"2.0","id":"$id","method":"workspace/reload"}""")
    val response = svr.waitForResponse(30.seconds, id)
    assert(response.contains("\"error\"") != expectSuccess, response)
  }

  private def checkReload(bsp: Boolean): Unit = {
    Using.resource(new WatchClient) { client =>
      val channel = owner()
      write("build-name", "after")
      if (bsp) bspReload(expectSuccess = true)
      else assert(runBatchClient("reload") == 0)
      assert(client.awaitExit() == 0)
      assert(stops("marker") == List(s"stop:marker:$channel:before:before:CancelWatch"))
      val events = read("events")
      assert(events.indexOf("stop:marker:") < events.indexOf("unload:before"), events)
      assert(events.linesIterator.count(_ == "unload:before") == 1, events)
      assert(client.text.contains("stopped because the build is being reloaded"), client.text)
      assert(client.text.contains("Run '~marker' again to restart the watch."), client.text)
      assert(runBatchClient("show name") == 0)
    }
    Using.resource(new WatchClient) { client =>
      awaitText("iteration-marker")(_.endsWith(":v0"))
      write("input", "v1")
      awaitText("iteration-marker")(_.endsWith(":v1"))
      assert(!client.isDone)
    }
  }

  test("stop another client's watch on reload") { _ =>
    checkReload(bsp = false)
  }

  test("stop another client's watch on BSP workspace/reload") { _ =>
    checkReload(bsp = true)
  }

  test("return an interactive watch client to its prompt after another client's reload") { _ =>
    Using.resource(new WatchClient(interactive = true)) { client =>
      val channel = owner()
      write("build-name", "after")
      assert(runBatchClient("reload") == 0)
      assert(waitUntil(20.seconds)(client.text.contains("sbt:after")), client.text)
      assert(!client.isDone)
      write("input", "v1")
      client.send("marker")
      awaitText("iteration-marker")(_ == s"$channel:v1")
      ()
    }
  }

  test("preserve the reload client's next watch and commands queued by termination") { _ =>
    Using.resource(new WatchClient) { first =>
      val firstOwner = owner()
      write("queue-callback")
      Using.resource(new WatchClient("reload; ~other")) { second =>
        owner("other")
        assert(first.awaitExit() == 0)
        awaitText("callback-queued")(_ == s"$firstOwner:none")
        assert(!second.isDone, "The reload client's queued watch was removed")
        write("input", "v1")
        awaitText("iteration-other")(_.endsWith(":v1"))
        ()
      }
    }
  }

  test("stop multiple watches and invoke each termination callback once") { _ =>
    Using.resource(new WatchClient) { first =>
      owner()
      Using.resource(new WatchClient("~other")) { second =>
        owner("other")
        assert(runBatchClient("reload") == 0)
        assert(first.awaitExit() == 0)
        assert(second.awaitExit() == 0)
        assert(stops("marker").size == 1)
        assert(stops("other").size == 1)
      }
    }
  }

  test("keep watching when another client's build fails to load") { _ =>
    Using.resource(new WatchClient) { client =>
      owner()
      write("build.sbt", "val = =\n")
      try bspReload(expectSuccess = false)
      finally write("build.sbt", originalBuild)
      assert(stops("marker").isEmpty)
      assert(
        !read("events").contains("unload:"),
        "The failed reload ran the old build's unload hook"
      )
      write("input", "v1")
      awaitText("iteration-marker")(_.endsWith(":v1"))
      assert(!client.isDone)
    }
  }

  private def checkLateFailure(control: String): Unit = {
    Using.resource(new WatchClient) { client =>
      owner()
      Using.resource(new WatchClient("~other")) { second =>
        owner("other")
        write(control)
        bspReload(expectSuccess = false)
        client.awaitExit()
        second.awaitExit()
        assert(stops("marker").size == 1)
        assert(stops("other").size == 1)
        assert(runBatchClient("reload") == 0)
        assert(stops("marker").size == 1, "Rollback repeated watch cleanup")
        assert(stops("other").size == 1, "Rollback repeated watch cleanup")
      }
    }
    Using.resource(new WatchClient) { client =>
      owner()
      write("input", "recovered")
      awaitText("iteration-marker")(_.endsWith(":recovered"))
      assert(!client.isDone)
    }
  }

  test("discard stopped watches after a throwing new-build load hook") { _ =>
    checkLateFailure("fail-load")
  }

  test("discard stopped watches after a throwing old-build unload hook") { _ =>
    checkLateFailure("fail-unload")
  }

  private def checkTerminationFailure(bsp: Boolean): Unit = {
    Using.resource(new WatchClient) { first =>
      val firstOwner = owner()
      Using.resource(new WatchClient("~other")) { second =>
        val secondOwner = owner("other")
        write("fail-termination")
        if (bsp) bspReload(expectSuccess = true)
        else assert(runBatchClient("reload") == 0)
        assert(first.awaitExit() == 0)
        assert(second.awaitExit() == 0)
        assert(stops("marker").size == 1)
        assert(stops("other").size == 1)
        val events = read("events")
        assert(events.linesIterator.count(_.startsWith("throw:")) == 1, events)
        List(("marker", firstOwner, first), ("other", secondOwner, second)).foreach {
          case (command, channel, client) =>
            val failed = events.contains(s"throw:$command:$channel")
            assert(client.text.contains("watch termination failed") == failed, client.text)
        }
      }
    }
    Using.resource(new WatchClient) { client =>
      owner()
      write("input", "recovered")
      awaitText("iteration-marker")(_.endsWith(":recovered"))
      assert(!client.isDone)
    }
  }

  test("complete another client's reload when a termination callback throws") { _ =>
    checkTerminationFailure(bsp = false)
  }

  test("complete BSP reload when a termination callback throws") { _ =>
    checkTerminationFailure(bsp = true)
  }

  test("preserve a watch's own automatic reload and restart") { _ =>
    write("automatic-reload")
    assert(runBatchClient("reload") == 0)
    Using.resource(new WatchClient) { client =>
      val channel = owner()
      Using.resource(new WatchClient("~other")) { second =>
        owner("other")
        write("build.sbt", originalBuild + "\nversion := \"after\"\n")
        awaitText("events")(_.contains(s"stop:marker:$channel:before:before:Reload"))
        awaitText("events")(_.linesIterator.count(_ == s"run:marker:$channel") >= 2)
        assert(second.awaitExit() == 0)
        assert(stops("other").size == 1)
        write("input", "v1")
        awaitText("iteration-marker")(_.endsWith(":v1"))
        assert(!client.isDone)
      }
    }
  }
}
