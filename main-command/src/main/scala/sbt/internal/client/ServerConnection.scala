/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt
package internal
package client

import java.net.{ SocketTimeoutException, Socket }
import java.util.concurrent.atomic.AtomicBoolean
import sbt.protocol._
import sbt.internal.util.StringEvent

abstract class ServerConnection(connection: Socket) {

  private val running = new AtomicBoolean(true)
  private val delimiter: Byte = '\n'.toByte

  private val out = connection.getOutputStream

  val thread = new Thread(s"sbt-serverconnection-${connection.getPort}") {
    override def run(): Unit = {
      try {
        val readBuffer = new Array[Byte](4096)
        val in = connection.getInputStream
        connection.setSoTimeout(5000)
        var buffer: Vector[Byte] = Vector.empty
        var bytesRead = 0
        while (bytesRead != -1 && running.get) {
          try {
            bytesRead = in.read(readBuffer)
            buffer = buffer ++ readBuffer.toVector.take(bytesRead)
            // handle un-framing
            var delimPos = buffer.indexOf(delimiter)
            while (delimPos > -1) {
              val chunk = buffer.take(delimPos)
              buffer = buffer.drop(delimPos + 1)

              Serialization
                .deserializeEvent(chunk)
                .fold(
                  { errorDesc =>
                    val s = new String(chunk.toArray, "UTF-8")
                    println(s"Got invalid chunk from server: $s \n" + errorDesc)
                  },
                  _ match {
                    case event: EventMessage => onEvent(event)
                    case event: StringEvent  => onLogEntry(event)
                  }
                )
              delimPos = buffer.indexOf(delimiter)
            }

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

  def publish(command: Array[Byte]): Unit = {
    out.write(command)
    out.write(delimiter.toInt)
    out.flush()
  }

  def onEvent(event: EventMessage): Unit
  def onLogEntry(event: StringEvent): Unit

  def onShutdown(): Unit

  def shutdown(): Unit = {
    println("Shutting down client connection")
    running.set(false)
    out.close()
    onShutdown
  }

}
