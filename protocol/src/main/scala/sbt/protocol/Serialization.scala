/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package protocol

import sjsonnew.{ JsonFormat, JsonWriter }
import sjsonnew.support.scalajson.unsafe.{ Parser, Converter, CompactPrinter }
import sjsonnew.shaded.scalajson.ast.unsafe.{ JValue, JObject, JString }
import java.nio.ByteBuffer
import java.util.UUID
import scala.util.{ Success, Failure }
import sbt.internal.util.StringEvent
import sbt.internal.protocol.{
  JsonRpcMessage,
  JsonRpcRequestMessage,
  JsonRpcResponseMessage,
  JsonRpcNotificationMessage
}

object Serialization {
  private[sbt] val VsCode = "application/vscode-jsonrpc; charset=utf-8"
  val readSystemIn = "sbt/readSystemIn"
  val cancelReadSystemIn = "sbt/cancelReadSystemIn"
  val systemIn = "sbt/systemIn"
  val systemOut = "sbt/systemOut"
  val systemErr = "sbt/systemErr"
  val systemOutFlush = "sbt/systemOutFlush"
  val systemErrFlush = "sbt/systemErrFlush"
  val terminalPropertiesQuery = "sbt/terminalPropertiesQuery"
  val terminalPropertiesResponse = "sbt/terminalPropertiesResponse"
  val terminalCapabilities = "sbt/terminalCapabilities"
  val terminalCapabilitiesResponse = "sbt/terminalCapabilitiesResponse"
  val attach = "sbt/attach"
  val attachResponse = "sbt/attachResponse"
  val cancelRequest = "sbt/cancelRequest"
  val promptChannel = "sbt/promptChannel"
  val setTerminalAttributes = "sbt/setTerminalAttributes"
  val getTerminalAttributes = "sbt/getTerminalAttributes"
  val terminalGetSize = "sbt/terminalGetSize"
  val terminalSetSize = "sbt/terminalSetSize"
  val terminalSetEcho = "sbt/terminalSetEcho"
  val terminalSetRawMode = "sbt/terminalSetRawMode"
  val CancelAll = "__CancelAll"

  @deprecated("unused", since = "1.4.0")
  def serializeEvent[A: JsonFormat](event: A): Array[Byte] = {
    val json: JValue = Converter.toJson[A](event).get
    CompactPrinter(json).getBytes("UTF-8")
  }

  @deprecated("unused", since = "1.4.0")
  def serializeCommand(command: CommandMessage): Array[Byte] = {
    import codec.JsonProtocol._
    val json: JValue = Converter.toJson[CommandMessage](command).get
    CompactPrinter(json).getBytes("UTF-8")
  }

  private[sbt] def serializeCommandAsJsonMessage(command: CommandMessage): String = {
    import sjsonnew.BasicJsonProtocol._

    command match {
      case x: InitCommand =>
        val execId = x.execId.getOrElse(UUID.randomUUID.toString)
        val analysis = s""""skipAnalysis" : ${x.skipAnalysis.getOrElse(false)}"""
        val opt = x.token match {
          case Some(t) =>
            val json: JValue = Converter.toJson[String](t).get
            val v = CompactPrinter(json)
            s"""{ "token": $v, $analysis }"""
          case None => s"{ $analysis }"
        }
        s"""{ "jsonrpc": "2.0", "id": "$execId", "method": "initialize", "params": { "initializationOptions": $opt } }"""
      case x: ExecCommand =>
        val execId = x.execId.getOrElse(UUID.randomUUID.toString)
        val json: JValue = Converter.toJson[String](x.commandLine).get
        val v = CompactPrinter(json)
        s"""{ "jsonrpc": "2.0", "id": "$execId", "method": "sbt/exec", "params": { "commandLine": $v } }"""
      case x: SettingQuery =>
        val execId = UUID.randomUUID.toString
        val json: JValue = Converter.toJson[String](x.setting).get
        val v = CompactPrinter(json)
        s"""{ "jsonrpc": "2.0", "id": "$execId", "method": "sbt/setting", "params": { "setting": $v } }"""

      case x: Attach =>
        val execId = UUID.randomUUID.toString
        val json: JValue = Converter.toJson[Boolean](x.interactive).get
        val v = CompactPrinter(json)
        s"""{ "jsonrpc": "2.0", "id": "$execId", "method": "$attach", "params": { "interactive": $v } }"""

    }
  }

  def serializeEventMessage(event: EventMessage): Array[Byte] = {
    import codec.JsonProtocol._
    val json: JValue = Converter.toJson[EventMessage](event).get
    CompactPrinter(json).getBytes("UTF-8")
  }

  /** This formats the message according to JSON-RPC. https://www.jsonrpc.org/specification */
  private[sbt] def serializeResponseMessage(message: JsonRpcResponseMessage): Array[Byte] = {
    import sbt.internal.protocol.codec.JsonRPCProtocol.given
    serializeResponse(message)
  }

  /** This formats the message according to JSON-RPC. https://www.jsonrpc.org/specification */
  private[sbt] def serializeRequestMessage(message: JsonRpcRequestMessage): Array[Byte] = {
    import sbt.internal.protocol.codec.JsonRPCProtocol.given
    serializeResponse(message)
  }

  /** This formats the message according to JSON-RPC. https://www.jsonrpc.org/specification */
  private[sbt] def serializeNotificationMessage(
      message: JsonRpcNotificationMessage,
  ): Array[Byte] = {
    import sbt.internal.protocol.codec.JsonRPCProtocol.given
    serializeResponse(message)
  }

  private[sbt] def serializeResponse[A: JsonWriter](message: A): Array[Byte] = {
    val json: JValue = Converter.toJson[A](message).get
    val body = CompactPrinter(json)
    val bodyLength = body.getBytes("UTF-8").length

    Iterator(
      s"Content-Length: $bodyLength",
      s"Content-Type: $VsCode",
      "",
      body
    ).mkString("\r\n").getBytes("UTF-8")
  }

  /**
   * @return
   *   A command or an invalid input description
   */
  @deprecated("unused", since = "1.4.0")
  def deserializeCommand(bytes: Seq[Byte]): Either[String, CommandMessage] = {
    val buffer = ByteBuffer.wrap(bytes.toArray)
    Parser.parseFromByteBuffer(buffer) match {
      case Success(json) =>
        import codec.JsonProtocol._
        Converter.fromJson[CommandMessage](json) match {
          case Success(command) => Right(command)
          case Failure(e)       => Left(e.getMessage)
        }
      case Failure(e) =>
        Left(s"Parse error: ${e.getMessage}")
    }
  }

  /**
   * @return
   *   A command or an invalid input description
   */
  @deprecated("unused", since = "1.4.0")
  def deserializeEvent(bytes: Seq[Byte]): Either[String, Any] = {
    val buffer = ByteBuffer.wrap(bytes.toArray)
    Parser.parseFromByteBuffer(buffer) match {
      case Success(json) =>
        detectType(json) match {
          case Some("StringEvent") =>
            import sbt.internal.util.codec.JsonProtocol._
            Converter.fromJson[StringEvent](json) match {
              case Success(event) => Right(event)
              case Failure(e)     => Left(e.getMessage)
            }
          case _ =>
            import codec.JsonProtocol._
            Converter.fromJson[EventMessage](json) match {
              case Success(event) => Right(event)
              case Failure(e)     => Left(e.getMessage)
            }
        }
      case Failure(e) =>
        Left(s"Parse error: ${e.getMessage}")
    }
  }

  def detectType(json: JValue): Option[String] =
    json match {
      case JObject(fields) =>
        (fields find { _.field == "type" } map { _.value }) match {
          case Some(JString(value)) => Some(value)
          case _                    => None
        }
      case _ => None
    }

  /**
   * @return
   *   A command or an invalid input description
   */
  @deprecated("unused", since = "1.4.0")
  def deserializeEventMessage(bytes: Seq[Byte]): Either[String, EventMessage] = {
    val buffer = ByteBuffer.wrap(bytes.toArray)
    Parser.parseFromByteBuffer(buffer) match {
      case Success(json) =>
        import codec.JsonProtocol._
        Converter.fromJson[EventMessage](json) match {
          case Success(event) => Right(event)
          case Failure(e)     => Left(e.getMessage)
        }
      case Failure(e) =>
        Left(s"Parse error: ${e.getMessage}")
    }
  }

  private[sbt] def deserializeJsonMessage(bytes: Seq[Byte]): Either[String, JsonRpcMessage] = {
    val buffer = ByteBuffer.wrap(bytes.toArray)
    Parser.parseFromByteBuffer(buffer) match {
      case Success(json @ JObject(fields)) =>
        import sbt.internal.protocol.codec.JsonRPCProtocol.given
        if ((fields find { _.field == "method" }).isDefined) {
          if ((fields find { _.field == "id" }).isDefined)
            Converter.fromJson[JsonRpcRequestMessage](json) match {
              case Success(request) => Right(request)
              case Failure(e)       => Left(s"conversion error: ${e.getMessage}")
            }
          else
            Converter.fromJson[JsonRpcNotificationMessage](json) match {
              case Success(notification) => Right(notification)
              case Failure(e)            => Left(s"conversion error: ${e.getMessage}")
            }
        } else if ((fields find { _.field == "id" }).isDefined)
          Converter.fromJson[JsonRpcResponseMessage](json) match {
            case Success(res) => Right(res)
            case Failure(e)   => Left(s"conversion error: ${e.getMessage}")
          }
        else Left(s"expected JSON-RPC object but found ${new String(bytes.toArray, "UTF-8")}")
      case Success(json) =>
        Left(s"expected JSON object but found ${new String(bytes.toArray, "UTF-8")}")
      case Failure(e) =>
        Left(s"parse error: ${e.getMessage}")
    }
  }

  private[sbt] def compactPrintJsonOpt(jsonOpt: Option[JValue]): String = {
    jsonOpt match {
      case Some(x) => CompactPrinter(x)
      case _       => ""
    }
  }
}
