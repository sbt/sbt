/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package testpkg

import scala.concurrent.duration._
import java.util.concurrent.atomic.AtomicInteger

// starts svr using server-test/events and perform event related tests
object EventsTest extends AbstractServerTest {
  override val testDirectory: String = "events"
  val currentID = new AtomicInteger(1000)

  test("report task failures in case of exceptions") { _ =>
    val id = currentID.getAndIncrement()
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": $id, "method": "sbt/exec", "params": { "commandLine": "hello" } }"""
    )
    assert(svr.waitForString(10.seconds) { s =>
      (s contains s""""id":$id""") && (s contains """"error":""")
    })
  }

  test("return error if cancelling non-matched task id") { _ =>
    val id = currentID.getAndIncrement()
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id":$id, "method": "sbt/exec", "params": { "commandLine": "run" } }"""
    )
    Thread.sleep(1000)
    val cancelID = currentID.getAndIncrement()
    val invalidID = currentID.getAndIncrement()
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id":$cancelID, "method": "sbt/cancelRequest", "params": { "id": "$invalidID" } }"""
    )
    assert(svr.waitForString(20.seconds) { s =>
      (s contains """"error":{"code":-32800""")
    })
    cancel(id)
  }

  test("cancel outer task after a nested evaluation completes") { _ =>
    cancelNested("nested", "nested-ready", List("nested-interrupted"))
  }

  test("cancel inner and outer tasks during a nested evaluation") { _ =>
    cancelNested("during", "inner-ready", List("inner-interrupted", "outer-interrupted"))
  }

  test("retry cancellation after the outer task swallows its first interrupt") { _ =>
    val ready = testPath.resolve("inner-ready").toFile
    sbt.io.IO.delete(ready)
    val id = exec("duringRetry")
    assert(waitUntil(10.seconds)(ready.exists()))
    cancel(id)
    assert(waitUntil(10.seconds)(testPath.resolve("retry-first-interrupted").toFile.exists()))
    sbt.io.IO.touch(testPath.resolve("retry-resume").toFile)
    assert(waitUntil(10.seconds)(testPath.resolve("retry-ready").toFile.exists()))
    cancel(id)
    assert(waitUntil(10.seconds)(testPath.resolve("retry-interrupted").toFile.exists()))
  }

  private def cancelNested(command: String, ready: String, interrupted: List[String]): Unit = {
    val id = exec(command)
    assert(waitUntil(10.seconds)(testPath.resolve(ready).toFile.exists()))
    val cancelled = cancelRequest(id)
    val responded = new java.util.concurrent.atomic.AtomicBoolean(false)
    assert(svr.waitForString(10.seconds) { s =>
      if (isResponse(id)(s)) responded.set(true)
      isResponse(cancelled)(s) && (s contains """"status":"Task cancelled"""")
    })
    interrupted.foreach(
      marker => assert(waitUntil(10.seconds)(testPath.resolve(marker).toFile.exists()))
    )
    assert(!testPath.resolve("outer-completed").toFile.exists())
    assert(responded.get || svr.waitForString(10.seconds)(isResponse(id)))
    val show = exec("show name")
    assert(svr.waitForString(10.seconds) { s =>
      isResponse(show)(s) && (s contains """"status":"Done"""")
    })
  }

  private def exec(commandLine: String): Int = {
    val id = currentID.getAndIncrement()
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": $id, "method": "sbt/exec", "params": { "commandLine": "$commandLine" } }"""
    )
    id
  }

  private def cancelRequest(id: Int): Int = {
    val cancelID = currentID.getAndIncrement()
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": $cancelID, "method": "sbt/cancelRequest", "params": { "id": "$id" } }"""
    )
    cancelID
  }

  private def cancel(id: Int): Unit = {
    val cancelID = cancelRequest(id)
    assert(svr.waitForString(10.seconds) { s =>
      isResponse(cancelID)(s) && (s contains """"status":"Task cancelled"""")
    })
  }

  private def isResponse(id: Int)(s: String): Boolean =
    s.matches(s""".*"id":\\s*"?$id"?[,}].*""") && !(s contains """"method":""")

  private def waitUntil(duration: FiniteDuration)(f: => Boolean): Boolean = {
    val deadline = duration.fromNow
    while (!f && !deadline.isOverdue()) Thread.sleep(50)
    f
  }

  /*
  test("cancel on-going task with numeric id") { _ =>
    val id = currentID.getAndIncrement()
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id":$id, "method": "sbt/exec", "params": { "commandLine": "run" } }"""
    )
    assert(svr.waitForString(20.seconds) { s =>
      s contains "Compiled events"
    })
    assert(svr.waitForString(10.seconds) { s =>
      s contains "running Main"
    })
    val cancelID = currentID.getAndIncrement()
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id":$cancelID, "method": "sbt/cancelRequest", "params": { "id": "$id" } }"""
    )
    assert(svr.waitForString(11.seconds) { s =>
      println(s)
      s contains """"result":{"status":"Task cancelled""""
    })
  }
   */

  /*
  test("cancel on-going task with string id") { _ =>
    import sbt.Exec
    val id = Exec.newExecId
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "$id", "method": "sbt/exec", "params": { "commandLine": "run" } }"""
    )
    assert(svr.waitForString(20.seconds) { s =>
      s contains "Compiled events"
    })
    val cancelID = Exec.newExecId
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "$cancelID", "method": "sbt/cancelRequest", "params": { "id": "$id" } }"""
    )
    assert(svr.waitForString(11.seconds) { s =>
      println(s)
      s contains """"result":{"status":"Task cancelled""""
    })
  }
 */
}
