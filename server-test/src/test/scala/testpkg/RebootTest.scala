/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package testpkg

/**
 * Regression for https://github.com/sbt/sbt/issues/9095: `reboot` from a client must bring the
 * server back and complete instead of leaving a zombie server that drops the client.
 */
class RebootTest extends AbstractServerTest {
  override val testDirectory: String = "client"

  test("reboot completes and the rebooted server serves the next command") {
    assert(runBatchClient("reboot") == 0, "reboot from a client must complete with exit 0")
    assert(
      runBatchClient("willSucceed") == 0,
      "the rebooted server must serve a new client connection"
    )
  }
}
