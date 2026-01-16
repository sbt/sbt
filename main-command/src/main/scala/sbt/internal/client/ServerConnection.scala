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
import java.net.{ Socket, SocketTimeoutException }
import java.util.concurrent.atomic.AtomicBoolean

import sbt.protocol.*
import sbt.internal.protocol.*
import sbt.internal.util.ReadJsonFromInputStream

abstract class ServerConnection(connection: Socket) {

  private val running = new AtomicBoolean(true)
  private val closed = new AtomicBoolean(false)
  private val retByte: Byte = '\r'.toByte
  private val delimiter: Byte = '\n'.toByte

  private val out = connection.getOutputStream

  val thread = new Thread(s"sbt-serverconnection-${connection.getPort}") {
    setDaemon(true)
    override def run(): Unit = {
      try {
        val in = connection.getInputStream
        connection.setSoTimeout(5000)
        while (running.get) {
          try {
            val frame = ReadJsonFromInputStream(in, running, None)
            if (running.get) {
              Serialization
                .deserializeJsonMessage(frame)
                .fold(
                  { errorDesc =>
                    val s = frame.mkString("") // new String(: Array[Byte], "UTF-8")
                    println(s"Got invalid chunk from server: $s \n" + errorDesc)
                  },
                  _ match {
                    case msg: JsonRpcRequestMessage      => onRequest(msg)
                    case msg: JsonRpcResponseMessage     => onResponse(msg)
                    case msg: JsonRpcNotificationMessage => onNotification(msg)
                  }
                )
            }
          } catch {
            case _: SocketTimeoutException => // its ok
            case e: IOException            => running.set(false)
          }
        }
      } finally {
        shutdown()
      }
    }
  }
  thread.start()

  def sendString(message: String): Unit = this.synchronized {
    val a = message.getBytes("UTF-8")
    writeLine(s"""Content-Length: ${a.length + 2}""".getBytes("UTF-8"))
    writeLine(Array())
    writeLine(a)
  }

  def writeLine(a: Array[Byte]): Unit =
    try {
      def writeEndLine(): Unit = {
        out.write(retByte.toInt)
        out.write(delimiter.toInt)
        out.flush
      }
      if (a.nonEmpty) {
        out.write(a)
      }
      writeEndLine()
    } catch {
      case e: IOException =>
        shutdown()
        throw e
    }

  def onRequest(msg: JsonRpcRequestMessage): Unit
  def onResponse(msg: JsonRpcResponseMessage): Unit
  def onNotification(msg: JsonRpcNotificationMessage): Unit

  def onShutdown(): Unit

  def shutdown(): Unit = if (closed.compareAndSet(false, true)) {
    if (!running.compareAndSet(true, false)) {
      System.err.println("\nsbt server connection closed.")
    }
    try {
      out.close()
      connection.close()
    } catch { case e: IOException => e.printStackTrace() }
    onShutdown()
  }

}
