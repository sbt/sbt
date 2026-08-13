/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package server

import sbt.internal.langserver.*
import sbt.internal.protocol.*
import sbt.internal.protocol.codec.*
import sbt.protocol.{ CompletionParams as CP, SettingQuery as Q }
import sjsonnew.shaded.scalajson.ast.unsafe.JValue
import sjsonnew.support.scalajson.unsafe.Converter
import xsbti.FileConverter

private[sbt] final case class LangServerError(code: Long, message: String)
    extends Throwable(message)

private[sbt] object LanguageServerProtocol {
  private val internalJsonProtocol = new sbt.internal.langserver.codec.JsonProtocol
    with sbt.protocol.codec.JsonProtocol
    with sjsonnew.BasicJsonProtocol
    with InitializeOptionFormats

  import internalJsonProtocol.given

  def json(r: JsonRpcRequestMessage): JValue =
    r.params.getOrElse(
      throw LangServerError(
        ErrorCodes.InvalidParams,
        s"param is expected on '${r.method}' method."
      )
    )

  lazy val serverCapabilities: ServerCapabilities = {
    ServerCapabilities(
      textDocumentSync = TextDocumentSyncOptions(true, 0, false, false, SaveOptions(false)),
      hoverProvider = false,
      definitionProvider = false
    )
  }

  def handler(converter: FileConverter): ServerHandler = ServerHandler { callback =>
    import callback.*

    def checkAuthenticated(r: JsonRpcRequestMessage)(f: => Unit): Unit =
      if !isAuthenticated then
        jsonRpcRespondError(
          Some(r.id),
          ErrorCodes.InvalidRequest,
          s"'${r.method}' is not allowed before authentication."
        )
      else f

    ServerIntent(
      onRequest = {
        case r: JsonRpcRequestMessage if r.method == "initialize" =>
          val param = Converter.fromJson[InitializeParams](json(r)).get
          val optionJson = param.initializationOptions.getOrElse(
            throw LangServerError(
              ErrorCodes.InvalidParams,
              "initializationOptions is expected on 'initialize' param."
            )
          )
          val opt = Converter.fromJson[InitializeOption](optionJson).get
          setInitializeOption(opt)
          if (authOptions(ServerAuthentication.Token)) {
            val token = opt.token.getOrElse(sys.error("'token' is missing."))
            if (authenticate(token)) ()
            else throw LangServerError(ErrorCodes.InvalidRequest, "invalid token")
          } else ()
          setInitialized(true)
          if (!opt.skipAnalysis.getOrElse(false)) appendExec("collectAnalyses", None)
          jsonRpcRespond(InitializeResult(serverCapabilities), Some(r.id))

        case r: JsonRpcRequestMessage if r.method == "sbt/exec" =>
          checkAuthenticated(r) {
            val param = Converter.fromJson[SbtExecParams](json(r)).get
            val _ = appendExec(param.commandLine, Some(r.id))
          }

        case r: JsonRpcRequestMessage if r.method == "sbt/setting" =>
          checkAuthenticated(r) {
            val param = Converter.fromJson[Q](json(r)).get
            onSettingQuery(Option(r.id), param)
          }

        case r: JsonRpcRequestMessage if r.method == "sbt/cancelRequest" =>
          checkAuthenticated(r) {
            val param = Converter.fromJson[CancelRequestParams](json(r)).get
            onCancellationRequest(Option(r.id), param)
          }

        case r: JsonRpcRequestMessage if r.method == "sbt/completion" =>
          checkAuthenticated(r) {
            val param = Converter.fromJson[CP](json(r)).get
            onCompletionRequest(Option(r.id), param)
          }

      },
      onResponse = PartialFunction.empty,
      onNotification = {
        case n: JsonRpcNotificationMessage if n.method == "textDocument/didSave" =>
          if (isAuthenticated) {
            val _ = appendExec(";Test/compile; collectAnalyses", None)
          } else log.warn(s"ignoring '${n.method}' before authentication")
      }
    )
  }
}
