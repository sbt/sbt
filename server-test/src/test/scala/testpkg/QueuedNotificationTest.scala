/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package testpkg

import scala.concurrent.duration.*
import java.util.concurrent.atomic.AtomicInteger

class QueuedNotificationTest extends AbstractServerTest {
  override val testDirectory: String = "queued"
  val currentID = new AtomicInteger(2000)

  test("send Queued notification when command is queued behind another") {
    val slowId = currentID.getAndIncrement()
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": $slowId, "method": "sbt/exec", "params": { "commandLine": "slowTask" } }"""
    )

    Thread.sleep(500)

    val quickId = currentID.getAndIncrement()
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": $quickId, "method": "sbt/exec", "params": { "commandLine": "quickTask" } }"""
    )

    assert(svr.waitForString(10.seconds) { s =>
      s.contains(""""status":"Queued"""") && s.contains(""""waiting for: slowTask"""")
    })

    assert(svr.waitForString(10.seconds) { s =>
      s.contains(s""""id":$slowId""") && s.contains(""""status":"Done"""")
    })

    assert(svr.waitForString(10.seconds) { s =>
      s.contains(s""""id":$quickId""") && s.contains(""""status":"Done"""")
    })
  }
}
