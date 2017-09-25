/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.typesafe.com>
 */
package sbt
package internal
package server

import java.net.{ Socket, SocketTimeoutException }
import java.util.concurrent.atomic.AtomicBoolean
import sbt.protocol._
import sjsonnew._

final class NetworkChannel(val name: String,
                           connection: Socket,
                           structure: BuildStructure,
                           auth: Set[ServerAuthentication],
                           instance: ServerInstance)
    extends CommandChannel {
  private val running = new AtomicBoolean(true)
  private val delimiter: Byte = '\n'.toByte
  private val out = connection.getOutputStream
  private var initialized = false

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
            var delimPos = buffer.indexOf(delimiter)
            while (delimPos > -1) {
              val chunk = buffer.take(delimPos)
              buffer = buffer.drop(delimPos + 1)

              Serialization
                .deserializeCommand(chunk)
                .fold(
                  errorDesc => println("Got invalid chunk from client: " + errorDesc),
                  onCommand
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

  def publishEvent[A: JsonFormat](event: A): Unit = {
    val bytes = Serialization.serializeEvent(event)
    publishBytes(bytes)
  }

  def publishEventMessage(event: EventMessage): Unit = {
    val bytes = Serialization.serializeEventMessage(event)
    publishBytes(bytes)
  }

  def publishBytes(event: Array[Byte]): Unit = {
    out.write(event)
    out.write(delimiter.toInt)
    out.flush()
  }

  def onCommand(command: CommandMessage): Unit = command match {
    case x: InitCommand  => onInitCommand(x)
    case x: ExecCommand  => onExecCommand(x)
    case x: SettingQuery => onSettingQuery(x)
  }

  private def onInitCommand(cmd: InitCommand): Unit = {
    if (auth(ServerAuthentication.Token)) {
      cmd.token match {
        case Some(x) =>
          instance.authenticate(x) match {
            case true =>
              initialized = true
              publishEventMessage(ChannelAcceptedEvent(name))
            case _ => sys.error("invalid token")
          }
        case None => sys.error("init command but without token.")
      }
    } else {
      initialized = true
    }
  }

  private def onExecCommand(cmd: ExecCommand) = {
    if (initialized) {
      append(
        Exec(cmd.commandLine, cmd.execId orElse Some(Exec.newExecId), Some(CommandSource(name))))
    } else {
      println(s"ignoring command $cmd before initialization")
    }
  }

  private def onSettingQuery(req: SettingQuery) = {
    if (initialized) {
      StandardMain.exchange publishEventMessage SettingQuery.handleSettingQuery(req, structure)
    } else {
      println(s"ignoring query $req before initialization")
    }
  }

  def shutdown(): Unit = {
    println("Shutting down client connection")
    running.set(false)
    out.close()
  }
}
