/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package testpkg

import scala.concurrent.duration._

// starts svr using server-test/buildserver and perform custom server tests
object BuildServerTest extends AbstractServerTest {
  override val testDirectory: String = "buildserver"

  test("build/initialize") { _ =>
    initializeRequest()
    assert(svr.waitForString(10.seconds) { s =>
      println(s)
      (s contains """"id":"10"""")
    })
  }

  test("workspace/buildTargets") { _ =>
    svr.sendJsonRpc(
      """{ "jsonrpc": "2.0", "id": "11", "method": "workspace/buildTargets", "params": {} }"""
    )
    assert(svr.waitForString(10.seconds) { s =>
      println(s)
      (s contains """"id":"11"""") &&
      (s contains """"displayName":"util"""")
    })
  }

  test("buildTarget/sources") { _ =>
    val x = s"${svr.baseDirectory.getAbsoluteFile.toURI}#util/Compile"
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "12", "method": "buildTarget/sources", "params": {
        |  "targets": [{ "uri": "$x" }]
        |} }""".stripMargin
    )
    assert(svr.waitForString(10.seconds) { s =>
      println(s)
      (s contains """"id":"12"""")
    })
  }

  def initializeRequest(): Unit = {
    svr.sendJsonRpc(
      """{ "jsonrpc": "2.0", "id": "10", "method": "build/initialize",
        |  "params": {
        |    "displayName": "test client",
        |    "version": "1.0.0",
        |    "bspVersion": "2.0.0-M5",
        |    "rootUri": "file://root/",
        |    "capabilities": { "languageIds": ["scala"] }
        |  }
        |}""".stripMargin
    )
  }
}
