/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.typesafe.com>
 */
package sbt
package server

import java.net.{ SocketTimeoutException, Socket }
import java.util.concurrent.atomic.AtomicBoolean

abstract class ClientConnection(connection: Socket) {

  // TODO handle client disconnect
  private val running = new AtomicBoolean(true)
  private val delimiter: Byte = '\n'.toByte

  private val out = connection.getOutputStream

  val thread = new Thread(s"sbt-client-${connection.getPort}") {
    override def run(): Unit = {
      val readBuffer = new Array[Byte](4096)
      val in = connection.getInputStream
      connection.setSoTimeout(5000)
      var buffer: Vector[Byte] = Vector.empty
      var bytesRead = 0
      while (bytesRead != -1 && running.get) {
        try {
          bytesRead = in.read(readBuffer)
          val bytes = readBuffer.toVector.take(bytesRead)
          buffer = buffer ++ bytes

          // handle un-framing
          val delimPos = bytes.indexOf(delimiter)
          if (delimPos > 0) {
            val chunk = buffer.take(delimPos)
            buffer = buffer.drop(delimPos)

            Serialization.deserialize(chunk).fold(
              errorDesc => println("Got invalid chunk from client: " + errorDesc),
              onCommand
            )
          }

        } catch {
          case _: SocketTimeoutException => // its ok
        }
      }

      shutdown()
    }
  }
  thread.start()

  def publish(event: Array[Byte]): Unit = {
    out.write(event)
    out.write(delimiter)
    out.flush()
  }

  def onCommand(command: Command): Unit

  def shutdown(): Unit = {
    println("Shutting down client connection")
    running.set(false)
    out.close()
  }

}
