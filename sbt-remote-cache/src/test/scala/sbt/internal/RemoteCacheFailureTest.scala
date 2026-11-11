package sbt
package internal

import com.eed3si9n.remoteapis.shaded.io.grpc.Status
import gigahorse.StatusError
import java.io.IOException
import sbt.util.CacheFailure
import scala.concurrent.TimeoutException

object RemoteCacheFailureTest extends verify.BasicTestSuite:
  import RemoteCacheFailure.classify

  /**
   * Classifies outside the assertion on purpose: verify's `assert` macro reifies the expression it
   * is given and re-emits a Java static path such as `Status.Code.OK` as a Scala module selection,
   * which then fails with `NoSuchFieldError` on `MODULE$`.
   */
  private def ofGrpc(codes: Status.Code*): Seq[CacheFailure] =
    codes.map(code => classify(code.toStatus().asRuntimeException()))

  test("A refusal is permanent, because permission does not change within a session"):
    val grpc = ofGrpc(Status.Code.PERMISSION_DENIED, Status.Code.UNAUTHENTICATED)
    assert(grpc.forall(_ == CacheFailure.Permanent), grpc.toString)
    assert(classify(StatusError(403)) == CacheFailure.Permanent)
    assert(classify(StatusError(401)) == CacheFailure.Permanent)

  test("An unreachable or overloaded cache is transient, so it is worth probing later"):
    val grpc = ofGrpc(
      Status.Code.UNAVAILABLE,
      Status.Code.DEADLINE_EXCEEDED,
      Status.Code.INTERNAL,
      Status.Code.RESOURCE_EXHAUSTED,
      Status.Code.ABORTED,
    )
    assert(grpc.forall(_ == CacheFailure.Transient), grpc.toString)
    assert(classify(StatusError(503)) == CacheFailure.Transient)
    assert(classify(new TimeoutException("waited too long")) == CacheFailure.Transient)
    assert(classify(new IOException("connection refused")) == CacheFailure.Transient)

  // A miss must not read as a failure, or the first key the cache does not hold would disable it.
  test("A miss and anything unrecognized count as an answer"):
    val grpc = ofGrpc(Status.Code.NOT_FOUND, Status.Code.INVALID_ARGUMENT, Status.Code.CANCELLED)
    assert(grpc.forall(_ == CacheFailure.Answered), grpc.toString)
    assert(classify(StatusError(404)) == CacheFailure.Answered)
    // A digest function the remote does not accept, which the rest of the build can work around.
    assert(classify(StatusError(400)) == CacheFailure.Answered)
    assert(classify(new RuntimeException("not found")) == CacheFailure.Answered)
end RemoteCacheFailureTest
