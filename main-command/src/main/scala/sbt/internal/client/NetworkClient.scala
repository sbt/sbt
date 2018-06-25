/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt
package internal
package client

import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicReference }
import scala.collection.mutable.ListBuffer
import scala.util.control.NonFatal
import scala.util.{ Success, Failure }
import sbt.protocol._
import sbt.internal.protocol._
import sbt.internal.langserver.{ LogMessageParams, MessageType, PublishDiagnosticsParams }
import sbt.internal.util.{ JLine, ConsoleAppender }
import sbt.util.Level
import sbt.io.syntax._
import sbt.io.IO
import sjsonnew.support.scalajson.unsafe.Converter

class NetworkClient(baseDirectory: File, arguments: List[String]) { self =>
  private val channelName = new AtomicReference("_")
  private val status = new AtomicReference("Ready")
  private val lock: AnyRef = new AnyRef {}
  private val running = new AtomicBoolean(true)
  private val pendingExecIds = ListBuffer.empty[String]

  private val console = ConsoleAppender("thin1")

  lazy val connection = init()

  start()

  // Open server connection based on the portfile
  def init(): ServerConnection = {
    val portfile = baseDirectory / "project" / "target" / "active.json"
    if (!portfile.exists) sys.error("server does not seem to be running.")
    val (sk, tkn) = ClientSocket.socket(portfile)
    val conn = new ServerConnection(sk) {
      override def onNotification(msg: JsonRpcNotificationMessage): Unit = self.onNotification(msg)
      override def onRequest(msg: JsonRpcRequestMessage): Unit = self.onRequest(msg)
      override def onResponse(msg: JsonRpcResponseMessage): Unit = self.onResponse(msg)
      override def onShutdown(): Unit = {
        running.set(false)
      }
    }
    // initiate handshake
    val execId = UUID.randomUUID.toString
    val initCommand = InitCommand(tkn, Option(execId))
    conn.sendString(Serialization.serializeCommandAsJsonMessage(initCommand))
    conn
  }

  /** Called on the response for a returning message. */
  def onReturningReponse(msg: JsonRpcResponseMessage): Unit = {
    def printResponse(): Unit = {
      msg.result match {
        case Some(result) =>
          // ignore result JSON
          console.success("completed")
        case _ =>
          msg.error match {
            case Some(err) =>
              // ignore err details
              console.appendLog(Level.Error, "completed")
            case _ => // ignore
          }
      }
    }
    printResponse()
  }

  def onResponse(msg: JsonRpcResponseMessage): Unit = {
    msg.id foreach {
      case execId if pendingExecIds contains execId =>
        onReturningReponse(msg)
        lock.synchronized {
          pendingExecIds -= execId
        }
      case _ =>
    }
  }

  def onNotification(msg: JsonRpcNotificationMessage): Unit = {
    def splitToMessage: Vector[(Level.Value, String)] =
      (msg.method, msg.params) match {
        case ("window/logMessage", Some(json)) =>
          import sbt.internal.langserver.codec.JsonProtocol._
          Converter.fromJson[LogMessageParams](json) match {
            case Success(params) => splitLogMessage(params)
            case Failure(e)      => Vector()
          }
        case ("textDocument/publishDiagnostics", Some(json)) =>
          import sbt.internal.langserver.codec.JsonProtocol._
          Converter.fromJson[PublishDiagnosticsParams](json) match {
            case Success(params) => splitDiagnostics(params)
            case Failure(e)      => Vector()
          }
        case _ =>
          Vector(
            (
              Level.Warn,
              s"unknown event: ${msg.method} " + Serialization.compactPrintJsonOpt(msg.params)
            )
          )
      }
    splitToMessage foreach {
      case (level, msg) => console.appendLog(level, msg)
    }
  }

  def splitLogMessage(params: LogMessageParams): Vector[(Level.Value, String)] = {
    val level = messageTypeToLevel(params.`type`)
    if (level == Level.Debug) Vector()
    else Vector((level, params.message))
  }

  def messageTypeToLevel(severity: Long): Level.Value = {
    severity match {
      case MessageType.Error   => Level.Error
      case MessageType.Warning => Level.Warn
      case MessageType.Info    => Level.Info
      case MessageType.Log     => Level.Debug
    }
  }

  def splitDiagnostics(params: PublishDiagnosticsParams): Vector[(Level.Value, String)] = {
    val uri = new URI(params.uri)
    val f = IO.toFile(uri)

    params.diagnostics map { d =>
      val level = d.severity match {
        case Some(severity) => messageTypeToLevel(severity)
        case _              => Level.Error
      }
      val line = d.range.start.line + 1
      val offset = d.range.start.character + 1
      val msg = s"$f:$line:$offset: ${d.message}"
      (level, msg)
    }
  }

  def onRequest(msg: JsonRpcRequestMessage): Unit = {
    // ignore
  }

  def start(): Unit = {
    console.appendLog(Level.Info, "entering *experimental* thin client - BEEP WHIRR")
    val userCommands = arguments filterNot { cmd =>
      cmd.startsWith("-")
    }
    if (userCommands.isEmpty) shell()
    else batchExecute(userCommands)
  }

  def batchExecute(userCommands: List[String]): Unit = {
    userCommands foreach { cmd =>
      println("> " + cmd)
      val execId = sendExecCommand(cmd)
      while (pendingExecIds contains execId) {
        Thread.sleep(100)
      }
    }
  }

  def shell(): Unit = {
    val reader = JLine.simple(None, JLine.HandleCONT, injectThreadSleep = true)
    while (running.get) {
      reader.readLine("> ", None) match {
        case Some("shutdown") =>
          // `sbt -client shutdown` shuts down the server
          sendExecCommand("exit")
        case Some("exit") =>
          running.set(false)
        case Some(s) if s.trim.nonEmpty =>
          val execId = sendExecCommand(s)
          while (pendingExecIds contains execId) {
            Thread.sleep(100)
          }
        case _ => //
      }
    }
  }

  def sendExecCommand(commandLine: String): String = {
    val execId = UUID.randomUUID.toString
    sendCommand(ExecCommand(commandLine, execId))
    lock.synchronized {
      pendingExecIds += execId
    }
    execId
  }

  def sendCommand(command: CommandMessage): Unit = {
    try {
      val s = Serialization.serializeCommandAsJsonMessage(command)
      connection.sendString(s)
    } catch {
      case _: IOException =>
      // log.debug(e.getMessage)
      // toDel += client
    }
    lock.synchronized {
      status.set("Processing")
    }
  }
}

object NetworkClient {
  def run(baseDirectory: File, arguments: List[String]): Unit =
    try {
      new NetworkClient(baseDirectory, arguments)
      ()
    } catch {
      case NonFatal(e) => println(e.getMessage)
    }
}
