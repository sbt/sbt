/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package testpkg

import sbt.protocol.{ CompletionParams, CompletionResponse }
import sbt.protocol.codec.JsonProtocol.given

// starts svr using server-test/completions and perform sbt/completion tests
class ServerCompletionsTest extends AbstractServerTest {
  override val testDirectory: String = "completions"

  test("return basic completions on request") {
    val response = svr.session
      .sendJsonRpcAwaitResult[CompletionResponse]("sbt/completion", CompletionParams(""))
      .get
    assert(response.items.nonEmpty)
  }

  test("return completion for custom tasks") {
    val response = svr.session
      .sendJsonRpcAwaitResult[CompletionResponse]("sbt/completion", CompletionParams("hell"))
      .get
    assert(response.items.contains("hello"))
  }

  test("return completions for user classes") {
    val response = svr.session
      .sendJsonRpcAwaitResult[CompletionResponse](
        "sbt/completion",
        CompletionParams("testOnly org.")
      )
      .get
    assert(response.items.contains("testOnly org.sbt.ExampleSpec"))
  }
}
