/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package testpkg

import java.nio.file.Files
import scala.concurrent.duration.*
import sbt.internal.protocol.JsonRpcRequestMessage

class ReloadFailureTest extends AbstractServerTest:
  override val testDirectory: String = "reload-failure"

  private def reload(): Boolean =
    val id = svr.session.nextId()
    svr.session.sendJsonRpc(JsonRpcRequestMessage("2.0", id, "workspace/reload", None)).get
    svr.session.waitForResponseMsg(30.seconds, id).get.error.isEmpty

  private def readInput(value: String): Unit =
    Files.writeString(testPath.resolve("input"), value)
    assert(runBatchClient("readInput") == 0, s"readInput failed for $value")
    assert(Files.readString(testPath.resolve("read-input")) == value)

  test("keep the file tree repository open when a build load hook fails"):
    readInput("before")
    Files.writeString(testPath.resolve("fail-load"), "")
    assert(!reload(), "The throwing load hook did not fail the reload")
    readInput("restored")
    assert(reload(), "The next reload failed")
    readInput("reloaded")
end ReloadFailureTest
