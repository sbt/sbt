/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package client

import java.io.IOException
import java.net.Socket

import sbt.protocol.*
import sbt.internal.protocol.*

/**
 * A server connection that extends [[ServerSession]] with immediate frame
 * dispatching. Incoming messages are deserialized and dispatched to
 * handlers ([[onRequest]], [[onResponse]], [[onNotification]]) on the
 * read thread.
 *
 * @param connection the connected socket to the sbt server
 */
abstract class ServerConnection(connection: Socket)
    extends ServerSession(connection, s"sbt-serverconnection-${connection.getPort}") {

  /**
   * Deserializes each incoming frame and dispatches to the appropriate
   * typed handler. Invalid frames are logged to stdout.
   */
  override protected def onFrame(frame: Seq[Byte]): Unit =
    Serialization
      .deserializeJsonMessage(frame)
      .fold(
        { errorDesc =>
          val s = new String(frame.toArray, "UTF-8")
          println(s"Got invalid chunk from server: $s \n" + errorDesc)
        },
        _ match {
          case msg: JsonRpcRequestMessage      => onRequest(msg)
          case msg: JsonRpcResponseMessage     => onResponse(msg)
          case msg: JsonRpcNotificationMessage => onNotification(msg)
        }
      )

  /** Called when [[close]] completes — triggers [[onShutdown]]. */
  override protected def onClose(): Unit = onShutdown()

  /** Sends a raw JSON-RPC message string. Synchronized and shuts down on I/O failure. */
  def sendString(message: String): Unit = this.synchronized {
    try {
      JsonRpcWriter.write(out, message)
    } catch {
      case e: IOException =>
        close()
        throw e
    }
  }

  /** Writes raw bytes as a JSON-RPC frame. Shuts down on I/O failure. */
  def writeLine(a: Array[Byte]): Unit =
    try {
      JsonRpcWriter.writeLine(out, a)
    } catch {
      case e: IOException =>
        close()
        throw e
    }

  def onRequest(msg: JsonRpcRequestMessage): Unit
  def onResponse(msg: JsonRpcResponseMessage): Unit
  def onNotification(msg: JsonRpcNotificationMessage): Unit

  /** Called exactly once when the connection is closed. */
  def onShutdown(): Unit

  /** Closes the connection. Delegates to [[close]]. */
  def shutdown(): Unit = close()

}
