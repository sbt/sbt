/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.protocol

import java.io.File
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import sbt.internal.protocol.JsonRpcRequestMessage
import sbt.internal.protocol.JsonRpcNotificationMessage
import sbt.internal.util.Util
import sjsonnew.BasicJsonProtocol.*

/**
 * A client session that never answers the terminal control queries (set echo, set raw
 * mode, attributes, size), for tests exercising the server's handling of a client that
 * stalls or dies with such a query outstanding.
 */
final class SilentTerminalSession(socket: Socket)
    extends ServerSessionImpl(socket, "silent-terminal-session-read-thread"):
  import SilentTerminalSession.silentMethods
  val silentQuery = new CountDownLatch(1)
  val firstSilentQuery = new AtomicReference[String]
  val inputRequested = new CountDownLatch(1)
  override protected def onRequest(msg: JsonRpcRequestMessage): Unit =
    msg.method match
      case m if silentMethods(m) =>
        Util.ignoreResult(firstSilentQuery.compareAndSet(null, m))
        silentQuery.countDown()
      case _ => Util.ignoreResult(sendJsonRpcResponse(msg.id, "bogus"))
  override protected def onNotification(msg: JsonRpcNotificationMessage): Unit =
    if msg.method == Serialization.readSystemIn then inputRequested.countDown()
    super.onNotification(msg)
end SilentTerminalSession

object SilentTerminalSession:
  private val silentMethods = Set(
    Serialization.terminalSetEcho,
    Serialization.terminalSetRawMode,
    Serialization.getTerminalAttributes,
    Serialization.setTerminalAttributes,
    Serialization.terminalGetSize,
    Serialization.terminalSetSize,
  )
  def connect(portfile: File): SilentTerminalSession =
    val (socket, _) = ClientSocket.socket(portfile, false)
    new SilentTerminalSession(socket)
end SilentTerminalSession
