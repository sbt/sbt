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
