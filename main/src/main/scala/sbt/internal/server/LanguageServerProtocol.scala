/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package server

import sjsonnew.JsonFormat
import sjsonnew.shaded.scalajson.ast.unsafe.JValue
import sjsonnew.support.scalajson.unsafe.Converter
import sbt.protocol.Serialization
import sbt.protocol.{ SettingQuery => Q, ExecStatusEvent }
import sbt.internal.protocol._
import sbt.internal.protocol.codec._
import sbt.internal.langserver._
import sbt.internal.util.ObjectEvent
import sbt.internal.util.complete.Parser
import sbt.util.Logger
import scala.util.control.NonFatal

private[sbt] final case class LangServerError(code: Long, message: String)
    extends Throwable(message)

private[sbt] object LanguageServerProtocol {
  lazy val internalJsonProtocol = new InitializeOptionFormats with sjsonnew.BasicJsonProtocol {}

  lazy val serverCapabilities: ServerCapabilities = {
    ServerCapabilities(
      textDocumentSync = TextDocumentSyncOptions(true, 0, false, false, SaveOptions(false)),
      hoverProvider = false,
      definitionProvider = true
    )
  }

  lazy val handler: ServerHandler = ServerHandler({
    case callback: ServerCallback =>
      import callback._
      ServerIntent(
        {
          import sbt.internal.langserver.codec.JsonProtocol._
          import internalJsonProtocol._
          def json(r: JsonRpcRequestMessage) =
            r.params.getOrElse(
              throw LangServerError(
                ErrorCodes.InvalidParams,
                s"param is expected on '${r.method}' method."
              )
            )

          {
            case r: JsonRpcRequestMessage if r.method == "initialize" =>
              if (authOptions(ServerAuthentication.Token)) {
                val param = Converter.fromJson[InitializeParams](json(r)).get
                val optionJson = param.initializationOptions.getOrElse(
                  throw LangServerError(
                    ErrorCodes.InvalidParams,
                    "initializationOptions is expected on 'initialize' param."
                  )
                )
                val opt = Converter.fromJson[InitializeOption](optionJson).get
                val token = opt.token.getOrElse(sys.error("'token' is missing."))
                if (authenticate(token)) ()
                else throw LangServerError(ErrorCodes.InvalidRequest, "invalid token")
              } else ()
              setInitialized(true)
              appendExec(Exec(s"collectAnalyses", None, Some(CommandSource(name))))
              jsonRpcRespond(InitializeResult(serverCapabilities), Option(r.id))

            case r: JsonRpcRequestMessage if r.method == "textDocument/definition" =>
              import scala.concurrent.ExecutionContext.Implicits.global
              Definition.lspDefinition(json(r), r.id, CommandSource(name), log)
              ()
            case r: JsonRpcRequestMessage if r.method == "textDocument/completion" =>
              try {
                val state = EvaluateTask.lastEvaluatedState.get

                val completionItems =
                  Parser
                    .completions(state.combinedParser, "", 9)
                    .get
                    .map(c => {
                      if (!c.isEmpty) Some(c.append.replaceAll("\n", " "))
                      else None
                    })
                    .flatten
                    .map(c => CompletionItem(label = c.toString))

                import sbt.protocol.codec.JsonProtocol._
                jsonRpcRespond(
                  CompletionList(
                    isIncomplete = false,
                    items = completionItems.toVector
                  ),
                  Option(r.id)
                )
              } catch {
                case NonFatal(e) =>
                  jsonRpcRespondError(
                    Some(r.id),
                    ErrorCodes.UnknownError,
                    "Completions request failed"
                  )
              }
            case r: JsonRpcRequestMessage if r.method == "sbt/exec" =>
              val param = Converter.fromJson[SbtExecParams](json(r)).get
              appendExec(Exec(param.commandLine, Some(r.id), Some(CommandSource(name))))
              ()
            case r: JsonRpcRequestMessage if r.method == "sbt/setting" =>
              import sbt.protocol.codec.JsonProtocol._
              val param = Converter.fromJson[Q](json(r)).get
              onSettingQuery(Option(r.id), param)
            case r: JsonRpcRequestMessage if r.method == "sbt/cancelRequest" =>
              val param = Converter.fromJson[CancelRequestParams](json(r)).get
              try {
                val (state, runningEngine) = EvaluateTask.currentlyRunningEngine.get
                val execId = state.currentCommand.get.execId.get

                // direct comparison on strings and
                // remove hotspring unicode added character for numbers
                if (execId == param.id || execId.substring(1).toLong == param.id.toLong) {
                  runningEngine.cancelAndShutdown()

                  import sbt.protocol.codec.JsonProtocol._
                  jsonRpcRespond(
                    ExecStatusEvent(
                      "Task cancelled",
                      Some(name),
                      Some(execId.toString),
                      Vector(),
                      None,
                    ),
                    Option(r.id)
                  )
                }
              } catch {
                case NonFatal(e) =>
                  jsonRpcRespondError(
                    Some(r.id),
                    ErrorCodes.RequestCancelled,
                    "Cancel request failed"
                  )
              }
          }
        }, {
          case n: JsonRpcNotificationMessage if n.method == "textDocument/didSave" =>
            appendExec(Exec(";Test/compile; collectAnalyses", None, Some(CommandSource(name))))
            ()
        }
      )
  })
}

/** Implements Language Server Protocol <https://github.com/Microsoft/language-server-protocol>. */
private[sbt] trait LanguageServerProtocol extends CommandChannel { self =>

  lazy val internalJsonProtocol = new InitializeOptionFormats with sjsonnew.BasicJsonProtocol {}

  protected def authenticate(token: String): Boolean
  protected def authOptions: Set[ServerAuthentication]
  protected def setInitialized(value: Boolean): Unit
  protected def log: Logger
  protected def onSettingQuery(execId: Option[String], req: Q): Unit

  protected lazy val callbackImpl: ServerCallback = new ServerCallback {
    def jsonRpcRespond[A: JsonFormat](event: A, execId: Option[String]): Unit =
      self.jsonRpcRespond(event, execId)

    def jsonRpcRespondError(execId: Option[String], code: Long, message: String): Unit =
      self.jsonRpcRespondError(execId, code, message)

    def jsonRpcNotify[A: JsonFormat](method: String, params: A): Unit =
      self.jsonRpcNotify(method, params)

    def appendExec(exec: Exec): Boolean = self.append(exec)
    def log: Logger = self.log
    def name: String = self.name
    private[sbt] def authOptions: Set[ServerAuthentication] = self.authOptions
    private[sbt] def authenticate(token: String): Boolean = self.authenticate(token)
    private[sbt] def setInitialized(value: Boolean): Unit = self.setInitialized(value)
    private[sbt] def onSettingQuery(execId: Option[String], req: Q): Unit =
      self.onSettingQuery(execId, req)
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
      // jsonRpcNotify("textDocument/publishDiagnostics", p)
      case "xsbti.Problem" =>
        () // ignore
      case _ =>
        // log.debug(event)
        ()
    }
  }

  /** Respond back to Language Server's client. */
  private[sbt] def jsonRpcRespond[A: JsonFormat](event: A, execId: Option[String]): Unit = {
    val m =
      JsonRpcResponseMessage("2.0", execId, Option(Converter.toJson[A](event).get), None)
    val bytes = Serialization.serializeResponseMessage(m)
    publishBytes(bytes)
  }

  /** Respond back to Language Server's client. */
  private[sbt] def jsonRpcRespondError(execId: Option[String], code: Long, message: String): Unit =
    jsonRpcRespondErrorImpl(execId, code, message, None)

  /** Respond back to Language Server's client. */
  private[sbt] def jsonRpcRespondError[A: JsonFormat](
      execId: Option[String],
      code: Long,
      message: String,
      data: A,
  ): Unit =
    jsonRpcRespondErrorImpl(execId, code, message, Option(Converter.toJson[A](data).get))

  private[this] def jsonRpcRespondErrorImpl(
      execId: Option[String],
      code: Long,
      message: String,
      data: Option[JValue],
  ): Unit = {
    val e = JsonRpcResponseError(code, message, data)
    val m = JsonRpcResponseMessage("2.0", execId, None, Option(e))
    val bytes = Serialization.serializeResponseMessage(m)
    publishBytes(bytes)
  }

  /** Notify to Language Server's client. */
  private[sbt] def jsonRpcNotify[A: JsonFormat](method: String, params: A): Unit = {
    val m =
      JsonRpcNotificationMessage("2.0", method, Option(Converter.toJson[A](params).get))
    log.debug(s"jsonRpcNotify: $m")
    val bytes = Serialization.serializeNotificationMessage(m)
    publishBytes(bytes)
  }

  def logMessage(level: String, message: String): Unit = {
    import sbt.internal.langserver.codec.JsonProtocol._
    jsonRpcNotify(
      "window/logMessage",
      LogMessageParams(MessageType.fromLevelString(level), message)
    )
  }
}
