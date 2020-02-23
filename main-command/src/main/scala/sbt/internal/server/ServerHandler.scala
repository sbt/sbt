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
import sbt.internal.protocol._
import sbt.util.Logger
import sbt.protocol.{ SettingQuery => Q, CompletionParams => CP }
import sbt.internal.langserver.{ CancelRequestParams => CRP }
import sbt.internal.bsp._

/**
 * ServerHandler allows plugins to extend sbt server.
 * It's a wrapper around curried function ServerCallback => JsonRpcRequestMessage => Unit.
 */
final class ServerHandler(val handler: ServerCallback => ServerIntent) {
  override def toString: String = s"Serverhandler(...)"
}

object ServerHandler {
  def apply(handler: ServerCallback => ServerIntent): ServerHandler =
    new ServerHandler(handler)

  lazy val fallback: ServerHandler = ServerHandler({ handler =>
    ServerIntent(
      { case x => handler.log.debug(s"Unhandled notification received: ${x.method}: $x") },
      { case x => handler.log.debug(s"Unhandled request received: ${x.method}: $x") }
    )
  })
}

final class ServerIntent(
    val onRequest: PartialFunction[JsonRpcRequestMessage, Unit],
    val onNotification: PartialFunction[JsonRpcNotificationMessage, Unit]
) {
  override def toString: String = s"ServerIntent(...)"
}

object ServerIntent {
  def apply(
      onRequest: PartialFunction[JsonRpcRequestMessage, Unit],
      onNotification: PartialFunction[JsonRpcNotificationMessage, Unit]
  ): ServerIntent =
    new ServerIntent(onRequest, onNotification)

  def request(onRequest: PartialFunction[JsonRpcRequestMessage, Unit]): ServerIntent =
    new ServerIntent(onRequest, PartialFunction.empty)

  def notify(onNotification: PartialFunction[JsonRpcNotificationMessage, Unit]): ServerIntent =
    new ServerIntent(PartialFunction.empty, onNotification)
}

/**
 * Interface to invoke JSON-RPC response.
 */
trait ServerCallback {
  def jsonRpcRespond[A: JsonFormat](event: A, execId: Option[String]): Unit
  def jsonRpcRespondError(execId: Option[String], code: Long, message: String): Unit
  def jsonRpcNotify[A: JsonFormat](method: String, params: A): Unit
  def appendExec(exec: Exec): Boolean
  def log: Logger
  def name: String

  private[sbt] def authOptions: Set[ServerAuthentication]
  private[sbt] def authenticate(token: String): Boolean
  private[sbt] def setInitialized(value: Boolean): Unit
  private[sbt] def onSettingQuery(execId: Option[String], req: Q): Unit
  private[sbt] def onCompletionRequest(execId: Option[String], cp: CP): Unit
  private[sbt] def onCancellationRequest(execId: Option[String], crp: CRP): Unit
  private[sbt] def onBspInitialize(execId: Option[String], param: InitializeBuildParams): Unit
  private[sbt] def onBspBuildTargets(execId: Option[String]): Unit
}
