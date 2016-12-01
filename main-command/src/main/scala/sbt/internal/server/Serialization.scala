/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.typesafe.com>
 */
package sbt
package internal
package server

import sjsonnew.support.scalajson.unsafe.{ Converter, CompactPrinter }
import scala.json.ast.unsafe.JValue
import sjsonnew.support.scalajson.unsafe.Parser
import java.nio.ByteBuffer
import scala.util.{ Success, Failure }
import sbt.protocol._

object Serialization {

  def serialize(event: EventMessage): Array[Byte] =
    {
      import codec.JsonProtocol._
      val json: JValue = Converter.toJson[EventMessage](event).get
      CompactPrinter(json).getBytes("UTF-8")
    }

  /**
   * @return A command or an invalid input description
   */
  def deserialize(bytes: Seq[Byte]): Either[String, CommandMessage] =
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
}
