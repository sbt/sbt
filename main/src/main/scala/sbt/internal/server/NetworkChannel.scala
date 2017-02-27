/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.typesafe.com>
 */
package sbt
package internal
package server

import java.net.{ Socket, SocketTimeoutException }
import java.util.concurrent.atomic.AtomicBoolean
import scala.util.{ Left, Right }
import sbt.protocol._
import sjsonnew._

final class NetworkChannel(val name: String, connection: Socket, state: State) extends CommandChannel {
  private val running = new AtomicBoolean(true)
  private val delimiter: Byte = '\n'.toByte
  private val out = connection.getOutputStream

  val thread = new Thread(s"sbt-networkchannel-${connection.getPort}") {
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
            val delimPos = buffer.indexOf(delimiter)
            if (delimPos > 0) {
              val chunk = buffer.take(delimPos)
              buffer = buffer.drop(delimPos + 1)

              Serialization.deserializeCommand(chunk).fold(
                errorDesc => println("Got invalid chunk from client: " + errorDesc),
                onCommand
              )
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

  def publishEvent[A: JsonFormat](event: A): Unit =
    {
      val bytes = Serialization.serializeEvent(event)
      publishBytes(bytes)
    }

  def publishEventMessage(event: EventMessage): Unit =
    {
      val bytes = Serialization.serializeEventMessage(event)
      publishBytes(bytes)
    }

  def publishBytes(event: Array[Byte]): Unit =
    {
      out.write(event)
      out.write(delimiter.toInt)
      out.flush()
    }

  def onCommand(command: CommandMessage): Unit = command match {
    case x: ExecCommand  => onExecCommand(x)
    case x: SettingQuery => onSettingQuery(x)
  }

  private def onExecCommand(cmd: ExecCommand) =
    append(Exec(cmd.commandLine, cmd.execId orElse Some(Exec.newExecId), Some(CommandSource(name))))

  private def onSettingQuery(req: SettingQuery) = {
    import sbt.internal.util.complete.Parser

    val extracted = Project extract state
    val key = Parser.parse(req.setting, Act scopedKeyParser extracted)

    def getSettingValue[A](key: Def.ScopedKey[A]) =
      extracted.structure.data.get(key.scope, key.key)
        .toRight(s"Key ${Def displayFull key} not found")
        .flatMap {
          case _: Task[_]      => Left(s"Key ${Def displayFull key} is a task, can only query settings")
          case _: InputTask[_] => Left(s"Key ${Def displayFull key} is an input task, can only query settings")
          case x               => Right(x)
        }

    val values = key match {
      case Left(msg)  => Left(s"Invalid programmatic input: $msg")
      case Right(key) => Right(getSettingValue(key))
    }

    val jsonValues = values match {
      case Left(errors) => errors
      case Right(value) => value.toString
    }

    StandardMain.exchange publishEventMessage SettingQueryResponse(jsonValues)
  }

  def shutdown(): Unit = {
    println("Shutting down client connection")
    running.set(false)
    out.close()
  }
}
