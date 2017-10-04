/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt
package protocol

import sjsonnew.JsonFormat
import sjsonnew.support.scalajson.unsafe.{ Parser, Converter, CompactPrinter }
import sjsonnew.shaded.scalajson.ast.unsafe.{ JValue, JObject, JString }
import java.nio.ByteBuffer
import scala.util.{ Success, Failure }
import sbt.internal.util.StringEvent
import sbt.internal.protocol.{
  JsonRpcRequestMessage,
  JsonRpcResponseMessage,
  JsonRpcNotificationMessage
}

object Serialization {
  private[sbt] val VsCode = "application/vscode-jsonrpc; charset=utf-8"

  def serializeEvent[A: JsonFormat](event: A): Array[Byte] = {
    val json: JValue = Converter.toJson[A](event).get
    CompactPrinter(json).getBytes("UTF-8")
  }

  def serializeCommand(command: CommandMessage): Array[Byte] = {
    import codec.JsonProtocol._
    val json: JValue = Converter.toJson[CommandMessage](command).get
    CompactPrinter(json).getBytes("UTF-8")
  }

  def serializeEventMessage(event: EventMessage): Array[Byte] = {
    import codec.JsonProtocol._
    val json: JValue = Converter.toJson[EventMessage](event).get
    CompactPrinter(json).getBytes("UTF-8")
  }

  /**
   * This formats the message according to JSON-RPC.
   * http://www.jsonrpc.org/specification
   */
  private[sbt] def serializeResponseMessage(message: JsonRpcResponseMessage): Array[Byte] = {
    import sbt.internal.protocol.codec.JsonRPCProtocol._
    val json: JValue = Converter.toJson[JsonRpcResponseMessage](message).get
    val body = CompactPrinter(json)
    val bodyBytes = body.getBytes("UTF-8")

    (s"Content-Length: ${bodyBytes.size}\r\n" +
      s"Content-Type: $VsCode\r\n" +
      "\r\n" +
      body).getBytes("UTF-8")
  }

  /**
   * This formats the message according to JSON-RPC.
   * http://www.jsonrpc.org/specification
   */
  private[sbt] def serializeNotificationMessage(
      message: JsonRpcNotificationMessage): Array[Byte] = {
    import sbt.internal.protocol.codec.JsonRPCProtocol._
    val json: JValue = Converter.toJson[JsonRpcNotificationMessage](message).get
    val body = CompactPrinter(json)
    val bodyBytes = body.getBytes("UTF-8")

    (s"Content-Length: ${bodyBytes.size}\r\n" +
      s"Content-Type: $VsCode\r\n" +
      "\r\n" +
      body).getBytes("UTF-8")
  }

  /**
   * @return A command or an invalid input description
   */
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
   * @return A command or an invalid input description
   */
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
   * @return A command or an invalid input description
   */
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

  private[sbt] def deserializeJsonRequest(
      bytes: Seq[Byte]): Either[String, JsonRpcRequestMessage] = {
    val buffer = ByteBuffer.wrap(bytes.toArray)
    Parser.parseFromByteBuffer(buffer) match {
      case Success(json) =>
        import sbt.internal.protocol.codec.JsonRPCProtocol._
        Converter.fromJson[JsonRpcRequestMessage](json) match {
          case Success(msg) => Right(msg)
          case Failure(e)   => throw e
        }
      case Failure(e) =>
        Left(s"Parse error: ${e.getMessage}")
    }
  }

  private[sbt] def compactPrintJsonOpt(jsonOpt: Option[JValue]): String = {
    jsonOpt match {
      case Some(x) => CompactPrinter(x)
      case _       => ""
    }
  }
}
