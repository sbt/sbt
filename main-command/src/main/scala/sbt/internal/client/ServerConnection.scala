/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package client

import java.net.{ SocketTimeoutException, Socket }
import java.util.concurrent.atomic.AtomicBoolean
import sbt.protocol._
import sbt.internal.protocol._

abstract class ServerConnection(connection: Socket) {

  private val running = new AtomicBoolean(true)
  private val retByte: Byte = '\r'.toByte
  private val delimiter: Byte = '\n'.toByte

  private val out = connection.getOutputStream

  val thread = new Thread(s"sbt-serverconnection-${connection.getPort}") {
    override def run(): Unit = {
      try {
        val readBuffer = new Array[Byte](4096)
        val in = connection.getInputStream
        connection.setSoTimeout(5000)
        var buffer: Vector[Byte] = Vector.empty
        def readFrame: Array[Byte] = {
          def getContentLength: Int = {
            readLine.drop(16).toInt
          }
          val l = getContentLength
          readLine
          readLine
          readContentLength(l)
        }

        def readLine: String = {
          if (buffer.isEmpty) {
            val bytesRead = in.read(readBuffer)
            if (bytesRead > 0) {
              buffer = buffer ++ readBuffer.toVector.take(bytesRead)
            }
          }
          val delimPos = buffer.indexOf(delimiter)
          if (delimPos > 0) {
            val chunk0 = buffer.take(delimPos)
            buffer = buffer.drop(delimPos + 1)
            // remove \r at the end of line.
            val chunk1 = if (chunk0.lastOption contains retByte) chunk0.dropRight(1) else chunk0
            new String(chunk1.toArray, "utf-8")
          } else readLine
        }

        def readContentLength(length: Int): Array[Byte] = {
          if (buffer.size < length) {
            val bytesRead = in.read(readBuffer)
            if (bytesRead > 0) {
              buffer = buffer ++ readBuffer.toVector.take(bytesRead)
            }
          }
          if (length <= buffer.size) {
            val chunk = buffer.take(length)
            buffer = buffer.drop(length)
            chunk.toArray
          } else readContentLength(length)
        }

        while (running.get) {
          try {
            val frame = readFrame
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
          } catch {
            case _: SocketTimeoutException => // its ok
          }
        }
      } finally {
        shutdown()
      }
    }
  }
  thread.start()

  def sendString(message: String): Unit = {
    val a = message.getBytes("UTF-8")
    writeLine(s"""Content-Length: ${a.length + 2}""".getBytes("UTF-8"))
    writeLine(Array())
    writeLine(a)
  }

  def writeLine(a: Array[Byte]): Unit = {
    def writeEndLine(): Unit = {
      out.write(retByte.toInt)
      out.write(delimiter.toInt)
      out.flush
    }
    if (a.nonEmpty) {
      out.write(a)
    }
    writeEndLine
  }

  def onRequest(msg: JsonRpcRequestMessage): Unit
  def onResponse(msg: JsonRpcResponseMessage): Unit
  def onNotification(msg: JsonRpcNotificationMessage): Unit

  def onShutdown(): Unit

  def shutdown(): Unit = {
    println("Shutting down client connection")
    running.set(false)
    out.close()
    onShutdown
  }

}
