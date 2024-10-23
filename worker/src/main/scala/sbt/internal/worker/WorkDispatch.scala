package sbt.internal.worker

import sbt.protocol.Serialization
import sbt.internal.langserver.ErrorCodes
import sbt.internal.protocol.{ JsonRpcMessage, JsonRpcNotificationMessage, JsonRpcRequestMessage }
import sbt.internal.worker.codec.JsonProtocol.given
import scala.util.Try
import sjsonnew.shaded.scalajson.ast.unsafe.JValue
import sjsonnew.support.scalajson.unsafe.Converter
import sbt.internal.protocol.JsonRpcResponseMessage
import sbt.internal.protocol.JsonRpcResponseError

class WorkDispatch(stdout: String => Unit):
  def request(line: String): Either[String, Unit] =
    for {
      rpc <- Serialization.deserializeJsonMessage(line.getBytes("UTF-8").toSeq)
      message <- handleJsonMessage(rpc)
    } yield ()

  // This handles the sbt/general method
  private def handleGeneral(id: String, params: GeneralParams): Try[Unit] =
    Try(WorkRun(params, stdout = stdoutHandler(id)))

  private def stdoutHandler(ref: String)(s: String): Unit =
    val notification = ConsoleNotification(
      ref = ref,
      stdout = Some(s),
      stderr = None,
    )
    val wrapper = JsonRpcNotificationMessage(
      jsonrpc = "2.0",
      method = "notify/console",
      params = Some(Converter.toJson(notification).get),
    )
    val json = Serialization.serializeNotificationMessageBody(wrapper)
    this.stdout(json + "\n")

  private def handleJsonMessage(message: JsonRpcMessage): Either[String, Unit] =
    message match
      case r: JsonRpcRequestMessage =>
        val result = r.method match
          case "sbt/general" =>
            Converter.fromJson[GeneralParams](json(r)).toEither match
              case Right(params) =>
                handleGeneral(r.id, params).toEither match
                  case Right(_)    => Right(())
                  case Left(value) => Left(value.getMessage())
              case Left(value) => Left(value.getMessage())
          case _ => Left(s"unknown method ${r.method}")
        sendResponse(r.id, result)
        result
      case _ => Left(s"JsonRpcMessage was not a request: $message")

  private def sendResponse(id: String, result: Either[String, Unit]): Unit =
    val res = result match
      case Right(_) =>
        JsonRpcResponseMessage(
          jsonrpc = "2.0",
          id = id,
          result = Some(Converter.toJson(0).get),
          error = None,
        )
      case Left(message) =>
        val error = JsonRpcResponseError(
          code = ErrorCodes.UnknownError,
          message = message,
        )
        JsonRpcResponseMessage(
          jsonrpc = "2.0",
          id = id,
          result = None,
          error = Some(error),
        )
    val json = Serialization.serializeResponseMessageBody(res)
    this.stdout(json + "\n")

  private def json(r: JsonRpcRequestMessage): JValue =
    r.params.getOrElse(
      throw WorkerError(
        ErrorCodes.InvalidParams,
        s"param is expected on '${r.method}' method."
      )
    )

  case class WorkerError(code: Long, message: String) extends Throwable(message)
end WorkDispatch
