/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.typesafe.com>
 */
package sbt
package protocol

import sjsonnew.support.scalajson.unsafe.{ Converter, CompactPrinter }
import scala.json.ast.unsafe.JValue
import sjsonnew.support.scalajson.unsafe.Parser
import java.nio.ByteBuffer
import scala.util.{ Success, Failure }

object Serialization {
  def serializeCommand(command: CommandMessage): Array[Byte] =
    {
      import codec.JsonProtocol._
      val json: JValue = Converter.toJson[CommandMessage](command).get
      CompactPrinter(json).getBytes("UTF-8")
    }

  def serializeEvent(event: EventMessage): Array[Byte] =
    {
      import codec.JsonProtocol._
      val json: JValue = Converter.toJson[EventMessage](event).get
      CompactPrinter(json).getBytes("UTF-8")
    }

  /**
   * @return A command or an invalid input description
   */
  def deserializeCommand(bytes: Seq[Byte]): Either[String, CommandMessage] =
    {
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
  def deserializeEvent(bytes: Seq[Byte]): Either[String, EventMessage] =
    {
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
}
