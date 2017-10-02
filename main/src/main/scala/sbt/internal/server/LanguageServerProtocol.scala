package sbt
package internal
package server

import sjsonnew.JsonFormat
import sjsonnew.support.scalajson.unsafe.Converter
import sbt.protocol.Serialization
import sbt.internal.protocol._
import sbt.internal.protocol.codec._
import sbt.internal.langserver._
import sbt.internal.util.ObjectEvent

private[sbt] case class LangServerError(code: Long, message: String) extends Throwable(message)

/**
 * Implements Language Server Protocol <https://github.com/Microsoft/language-server-protocol>.
 */
private[sbt] trait LanguageServerProtocol extends CommandChannel {

  lazy val internalJsonProtocol = new InitializeOptionFormats with sjsonnew.BasicJsonProtocol {}

  protected def authenticate(token: String): Boolean
  protected def authOptions: Set[ServerAuthentication]
  protected def setInitialized(value: Boolean): Unit

  protected def onRequestMessage(request: JsonRpcRequestMessage): Unit = {

    import sbt.internal.langserver.codec.JsonProtocol._
    import internalJsonProtocol._

    println(s"onRequestMessage: $request")
    request.method match {
      case "initialize" =>
        if (authOptions(ServerAuthentication.Token)) {
          val json =
            request.params.getOrElse(
              throw LangServerError(ErrorCodes.InvalidParams,
                                    "param is expected on 'initialize' method."))
          val param = Converter.fromJson[InitializeParams](json).get
          val optionJson = param.initializationOptions.getOrElse(
            throw LangServerError(ErrorCodes.InvalidParams,
                                  "initializationOptions is expected on 'initialize' param."))
          val opt = Converter.fromJson[InitializeOption](optionJson).get
          val token = opt.token.getOrElse(sys.error("'token' is missing."))
          if (authenticate(token)) ()
          else throw LangServerError(ErrorCodes.InvalidRequest, "invalid token")
        } else ()
        setInitialized(true)
        langRespond(InitializeResult(serverCapabilities), Option(request.id))
      case "textDocument/didSave" =>
        append(Exec("compile", Some(request.id), Some(CommandSource(name))))
      case _ => ()
    }
  }

  /**
   * This reacts to various events that happens inside sbt, sometime
   * in response to the previous requests.
   * The type information has been erased because it went through logging.
   */
  protected def onObjectEvent(event: ObjectEvent[_]): Unit = {
    // import sbt.internal.langserver.codec.JsonProtocol._

    val msgContentType = event.contentType
    msgContentType match {
      // LanguageServerReporter sends PublishDiagnosticsParams
      case "sbt.internal.langserver.PublishDiagnosticsParams" =>
      // val p = event.message.asInstanceOf[PublishDiagnosticsParams]
      // langNotify("textDocument/publishDiagnostics", p)
      case "xsbti.Problem" =>
        () // ignore
      case _ =>
        // println(event)
        ()
    }
  }

  /**
   * Respond back to Language Server's client.
   */
  private[sbt] def langRespond[A: JsonFormat](event: A, execId: Option[String]): Unit = {
    val m =
      JsonRpcResponseMessage("2.0", execId, Option(Converter.toJson[A](event).get), None)
    val bytes = Serialization.serializeResponseMessage(m)
    publishBytes(bytes)
  }

  /**
   * Respond back to Language Server's client.
   */
  private[sbt] def langError(execId: Option[String], code: Long, message: String): Unit = {
    val e = JsonRpcResponseError(code, message, None)
    val m = JsonRpcResponseMessage("2.0", execId, None, Option(e))
    val bytes = Serialization.serializeResponseMessage(m)
    publishBytes(bytes)
  }

  /**
   * Respond back to Language Server's client.
   */
  private[sbt] def langError[A: JsonFormat](execId: Option[String],
                                            code: Long,
                                            message: String,
                                            data: A): Unit = {
    val e = JsonRpcResponseError(code, message, Option(Converter.toJson[A](data).get))
    val m = JsonRpcResponseMessage("2.0", execId, None, Option(e))
    val bytes = Serialization.serializeResponseMessage(m)
    publishBytes(bytes)
  }

  /**
   * Notify to Language Server's client.
   */
  private[sbt] def langNotify[A: JsonFormat](method: String, params: A): Unit = {
    val m =
      JsonRpcNotificationMessage("2.0", method, Option(Converter.toJson[A](params).get))
    println(s"langNotify: $m")
    val bytes = Serialization.serializeNotificationMessage(m)
    publishBytes(bytes)
  }

  private[sbt] lazy val serverCapabilities: ServerCapabilities = {
    ServerCapabilities(textDocumentSync =
                         TextDocumentSyncOptions(true, 0, false, false, SaveOptions(false)),
                       hoverProvider = false)
  }
}
