package sbt.internal.client

import verify.BasicTestSuite

object NetworkClientExitCodeTest extends BasicTestSuite:

  test("server success with fork success returns 0"):
    val result = NetworkClient.effectiveExitCode(serverExitCode = 0, forkExitCode = 0)
    assert(result == 0)

  test("server success but fork failure returns fork exit code"):
    val result = NetworkClient.effectiveExitCode(serverExitCode = 0, forkExitCode = 1)
    assert(result == 1)

  test("server failure with no fork returns server exit code"):
    val result = NetworkClient.effectiveExitCode(serverExitCode = 1, forkExitCode = 0)
    assert(result == 1)

  test("server failure with fork failure returns server exit code"):
    val result = NetworkClient.effectiveExitCode(serverExitCode = 1, forkExitCode = 1)
    assert(result == 1)

  test("server failure with non-unit exit code is preserved"):
    val result = NetworkClient.effectiveExitCode(serverExitCode = 42, forkExitCode = 0)
    assert(result == 42)

  test("fork non-unit exit code is preserved when server reports success"):
    val result = NetworkClient.effectiveExitCode(serverExitCode = 0, forkExitCode = 137)
    assert(result == 137)

  test("server failure takes priority over different fork failure"):
    val result = NetworkClient.effectiveExitCode(serverExitCode = 2, forkExitCode = 137)
    assert(result == 2)

end NetworkClientExitCodeTest
