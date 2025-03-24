package sbt.internal.librarymanagement

import java.io.IOException

import org.scalatest.funsuite.AnyFunSuite
import sbt.internal.librarymanagement.IvyUtil.*

class IvyUtilSpec extends AnyFunSuite {
  test("503 should be a TransientNetworkException") {
    val statusCode503Exception =
      new IOException("Server returned HTTP response code: 503 for URL:")
    assert(TransientNetworkException(statusCode503Exception))
  }

  test("500 should be a TransientNetworkException") {
    val statusCode500Exception =
      new IOException("Server returned HTTP response code: 500 for URL:")
    assert(TransientNetworkException(statusCode500Exception))
  }

  test("408 should be a TransientNetworkException") {
    val statusCode408Exception =
      new IOException("Server returned HTTP response code: 408 for URL:")
    assert(TransientNetworkException(statusCode408Exception))
  }

  test("429 should be a TransientNetworkException") {
    val statusCode429Exception =
      new IOException(" Server returned HTTP response code: 429 for URL:")
    assert(TransientNetworkException(statusCode429Exception))
  }

  test("404 should not be a TransientNetworkException") {
    val statusCode404Exception =
      new IOException("Server returned HTTP response code: 404 for URL:")
    assert(!TransientNetworkException(statusCode404Exception))
  }

  test("IllegalArgumentException should not be a TransientNetworkException") {
    val illegalArgumentException = new IllegalArgumentException()
    assert(!TransientNetworkException(illegalArgumentException))
  }

  test("it should retry for 3 attempts") {
    var i = 0
    def f: Int = {
      i += 1
      if (i < 3) throw new RuntimeException() else i
    }
    // exception predicate retries on all exceptions for this test
    val result = retryWithBackoff(f, _ => true, maxAttempts = 3)
    assert(result == 3)
  }

  test("it should fail after maxAttempts") {
    var i = 0
    def f: Int = {
      i += 1
      throw new RuntimeException()
    }
    intercept[RuntimeException] {
      retryWithBackoff(f, _ => true, maxAttempts = 3)
    }
    assert(i == 3)
  }
}
