package sbt
package internal

import com.eed3si9n.remoteapis.shaded.io.grpc.StatusRuntimeException
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
    case e: StatusRuntimeException => fromGrpc(e.getStatus().getCode().name())
    case e: StatusError            => fromHttp(e.status)
    case _: TimeoutException       => CacheFailure.Transient
    // A refused connection or a reset socket, which gRPC would have reported as UNAVAILABLE.
    case _: IOException => CacheFailure.Transient
    case _              => CacheFailure.Answered

  /**
   * Matches the status by name because shading strips the enum flag from `Status.Code`, which
   * leaves its constants unreachable from Scala.
   */
  private def fromGrpc(code: String): CacheFailure = code match
    case "PERMISSION_DENIED" | "UNAUTHENTICATED" => CacheFailure.Permanent
    case "UNAVAILABLE" | "DEADLINE_EXCEEDED" | "INTERNAL" | "RESOURCE_EXHAUSTED" | "ABORTED" =>
      CacheFailure.Transient
    case _ => CacheFailure.Answered

  private def fromHttp(status: Int): CacheFailure =
    if status == 401 || status == 403 then CacheFailure.Permanent
    else if status >= 500 then CacheFailure.Transient
    else CacheFailure.Answered
end RemoteCacheFailure
