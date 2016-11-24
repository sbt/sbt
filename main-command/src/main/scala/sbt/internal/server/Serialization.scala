/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.typesafe.com>
 */
package sbt
package internal
package server

import sjsonnew.{ JsonFormat, BasicJsonProtocol }
import sjsonnew.support.scalajson.unsafe.{ Converter, CompactPrinter }
import scala.json.ast.unsafe.JValue
import sjsonnew.support.scalajson.unsafe.Parser
import java.nio.ByteBuffer
import scala.util.{ Success, Failure }

object Serialization {

  def serialize(event: Event): Array[Byte] =
    {
      import ServerCodec._
      val msg = toMessage(event)
      val json: JValue = Converter.toJson[EventMessage](msg).get
      CompactPrinter(json).getBytes("UTF-8")
    }
  def toMessage(event: Event): EventMessage =
    event match {
      case LogEvent(level, message) =>
        EventMessage(
          `type` = "logEvent",
          status = None, commandQueue = Vector(),
          level = Some(level), message = Some(message), success = None, commandLine = None
        )
      case StatusEvent(Ready) =>
        EventMessage(
          `type` = "statusEvent",
          status = Some("ready"), commandQueue = Vector(),
          level = None, message = None, success = None, commandLine = None
        )
      case StatusEvent(Processing(command, commandQueue)) =>
        EventMessage(
          `type` = "statusEvent",
          status = Some("processing"), commandQueue = commandQueue.toVector,
          level = None, message = None, success = None, commandLine = None
        )
      case ExecutionEvent(command, status) =>
        EventMessage(
          `type` = "executionEvent",
          status = None, commandQueue = Vector(),
          level = None, message = None, success = Some(status), commandLine = Some(command)
        )
    }

  /**
   * @return A command or an invalid input description
   */
  def deserialize(bytes: Seq[Byte]): Either[String, Command] =
    {
      val buffer = ByteBuffer.wrap(bytes.toArray)
      Parser.parseFromByteBuffer(buffer) match {
        case Success(json) =>
          import ServerCodec._
          Converter.fromJson[CommandMessage](json) match {
            case Success(command) =>
              command.`type` match {
                case "exec" =>
                  command.commandLine match {
                    case Some(cmd) => Right(Execution(cmd))
                    case None      => Left("Missing or invalid command_line field")
                  }
                case cmd => Left(s"Unknown command type $cmd")
              }
            case Failure(e) => Left(e.getMessage)
          }
        case Failure(e) =>
          Left(s"Parse error: ${e.getMessage}")
      }
    }
}

object ServerCodec extends ServerCodec
trait ServerCodec extends codec.EventMessageFormats with codec.CommandMessageFormats with BasicJsonProtocol
