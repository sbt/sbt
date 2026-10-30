package sbt
package internal

import com.eed3si9n.remoteapis.shaded.io.grpc.{ Status, StatusRuntimeException }
import gigahorse.StatusError
import java.io.IOException
import sbt.util.CacheFailure
import scala.concurrent.TimeoutException

object RemoteCacheFailureTest extends verify.BasicTestSuite:
  import RemoteCacheFailure.classify

  test("A refusal is permanent, because permission does not change within a session"):
    assert(classify(grpc("PERMISSION_DENIED")) == CacheFailure.Permanent)
    assert(classify(grpc("UNAUTHENTICATED")) == CacheFailure.Permanent)
    assert(classify(StatusError(403)) == CacheFailure.Permanent)
    assert(classify(StatusError(401)) == CacheFailure.Permanent)

  test("An unreachable or overloaded cache is transient, so it is worth probing later"):
    assert(classify(grpc("UNAVAILABLE")) == CacheFailure.Transient)
    assert(classify(grpc("DEADLINE_EXCEEDED")) == CacheFailure.Transient)
    assert(classify(grpc("RESOURCE_EXHAUSTED")) == CacheFailure.Transient)
    assert(classify(StatusError(503)) == CacheFailure.Transient)
    assert(classify(new TimeoutException("waited too long")) == CacheFailure.Transient)
    assert(classify(new IOException("connection refused")) == CacheFailure.Transient)

  // A miss must not read as a failure, or the first key the cache does not hold would disable it.
  test("A miss and anything unrecognized count as an answer"):
    assert(classify(grpc("NOT_FOUND")) == CacheFailure.Answered)
    assert(classify(StatusError(404)) == CacheFailure.Answered)
    assert(classify(new RuntimeException("not found")) == CacheFailure.Answered)
    assert(classify(grpc("INVALID_ARGUMENT")) == CacheFailure.Answered)

  /**
   * Builds the status by scanning the code values, because shading strips the enum flag from
   * `Status.Code`, which leaves its constants unreachable from Scala.
   */
  private def grpc(name: String): StatusRuntimeException =
    (0 to 16).iterator
      .map(Status.fromCodeValue)
      .find(_.getCode().name() == name)
      .getOrElse(sys.error(s"no gRPC status named $name"))
      .asRuntimeException()
end RemoteCacheFailureTest
