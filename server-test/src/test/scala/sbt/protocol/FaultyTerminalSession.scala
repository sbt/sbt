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
import sbt.internal.protocol.JsonRpcRequestMessage
import sbt.internal.util.Util
import sjsonnew.BasicJsonProtocol.*

/**
 * A client session that answers every server-to-client request with a result of the
 * wrong shape, for tests exercising the server's handling of malformed responses.
 */
final class FaultyTerminalSession(socket: Socket)
    extends ServerSessionImpl(socket, "faulty-terminal-session-read-thread"):
  val propertiesQueried = new java.util.concurrent.CountDownLatch(1)
  override protected def onRequest(msg: JsonRpcRequestMessage): Unit =
    if msg.method == Serialization.terminalPropertiesQuery then propertiesQueried.countDown()
    Util.ignoreResult(sendJsonRpcResponse(msg.id, "bogus"))
end FaultyTerminalSession

object FaultyTerminalSession:
  def connect(portfile: File): FaultyTerminalSession =
    val (socket, _) = ClientSocket.socket(portfile, false)
    new FaultyTerminalSession(socket)
end FaultyTerminalSession
