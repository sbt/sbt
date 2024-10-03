/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package testpkg

import scala.concurrent.duration._

// starts svr using server-test/completions and perform sbt/completion tests
class ServerCompletionsTest extends AbstractServerTest {
  override val testDirectory: String = "completions"

  test("return basic completions on request") {
    val completionStr = """{ "query": "" }"""
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": 15, "method": "sbt/completion", "params": $completionStr }"""
    )
    assert(svr.waitForString(10.seconds) { s =>
      s contains """"result":{"items":["""
    })
  }

  test("return completion for custom tasks") {
    val completionStr = """{ "query": "hell" }"""
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": 16, "method": "sbt/completion", "params": $completionStr }"""
    )
    assert(svr.waitForString(10.seconds) { s =>
      s contains """"result":{"items":["hello"]"""
    })
  }

  /*
  // TODO: https://github.com/sbt/sbt/issues/7718
  // Note that this test currently relies on a fake target artifact that's checked in
  test("return completions for user classes") {
    val completionStr = """{ "query": "testOnly org." }"""
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": 17, "method": "sbt/completion", "params": $completionStr }"""
    )
    assert(svr.waitForString(10.seconds) { s =>
      s contains """"result":{"items":["testOnly org.sbt.ExampleSpec"]"""
    })
  }
   */
}
