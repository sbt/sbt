package sbt
package internal

import com.eed3si9n.remoteapis.shaded.io.grpc.{ Status, StatusRuntimeException }
import gigahorse.StatusError
import java.io.IOException
import sbt.util.CacheFailure
import scala.concurrent.TimeoutException

object RemoteCacheFailure:

  /**
   * Tells a circuit breaker what a failed call to a remote cache means. Anything unrecognized
   * counts as an answer, so an unexpected failure keeps the cache in use rather than disabling it.
   */
  private[sbt] def classify(e: Throwable): CacheFailure = e match
    case e: StatusRuntimeException => fromGrpc(e.getStatus().getCode())
    case e: StatusError            => fromHttp(e.status)
    case _: TimeoutException       => CacheFailure.Transient
    // A refused connection or a reset socket, which gRPC would have reported as UNAVAILABLE.
    case _: IOException => CacheFailure.Transient
    case _              => CacheFailure.Answered

  private def fromGrpc(code: Status.Code): CacheFailure = code match
    case Status.Code.PERMISSION_DENIED | Status.Code.UNAUTHENTICATED => CacheFailure.Permanent
    case Status.Code.UNAVAILABLE | Status.Code.DEADLINE_EXCEEDED | Status.Code.INTERNAL |
        Status.Code.RESOURCE_EXHAUSTED | Status.Code.ABORTED =>
      CacheFailure.Transient
    case _ => CacheFailure.Answered

  private def fromHttp(status: Int): CacheFailure =
    if status == 401 || status == 403 then CacheFailure.Permanent
    else if status >= 500 then CacheFailure.Transient
    else CacheFailure.Answered

  /**
   * The status to report when uploads were refused for more than one reason. A withheld
   * permission outranks an outage, because it is the remote stating a fact about the token that
   * holds for the rest of the session, whereas a 5xx says only that this attempt failed.
   */
  private[sbt] def worstHttpStatus(statuses: Seq[Int]): Int =
    statuses.minBy: status =>
      fromHttp(status) match
        case CacheFailure.Permanent => 0
        case CacheFailure.Transient => 1
        case CacheFailure.Answered  => 2
end RemoteCacheFailure
