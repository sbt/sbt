/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.protocol.codec

import _root_.sjsonnew.{
  Builder,
  DeserializationException,
  JsonFormat,
  Unbuilder,
  deserializationError
}
import sjsonnew.shaded.scalajson.ast.unsafe._

trait JsonRpcResponseMessageFormats {
  self: sbt.internal.util.codec.JValueFormats &
    sbt.internal.protocol.codec.JsonRpcResponseErrorFormats & sjsonnew.BasicJsonProtocol =>
  given JsonRpcResponseMessageFormat: JsonFormat[sbt.internal.protocol.JsonRpcResponseMessage] =
    new JsonFormat[sbt.internal.protocol.JsonRpcResponseMessage] {
      override def read[J](
          jsOpt: Option[J],
          unbuilder: Unbuilder[J]
      ): sbt.internal.protocol.JsonRpcResponseMessage = {
        jsOpt match {
          case Some(js) =>
            unbuilder.beginObject(js)
            val jsonrpc = unbuilder.readField[String]("jsonrpc")
            val id =
              try {
                unbuilder.readField[String]("id")
              } catch {
                case _: DeserializationException =>
                  unbuilder.readField[Long]("id").toString
              }

            val result = unbuilder.lookupField("result") map { case x: JValue =>
              x
            }

            val error =
              unbuilder.readField[Option[sbt.internal.protocol.JsonRpcResponseError]]("error")

            unbuilder.endObject()
            sbt.internal.protocol.JsonRpcResponseMessage(jsonrpc, id, result, error)
          case None =>
            deserializationError("Expected JsObject but found None")
        }
      }
      override def write[J](
          obj: sbt.internal.protocol.JsonRpcResponseMessage,
          builder: Builder[J]
      ): Unit = {
        // Parse given id to Long or String judging by prefix
        def parseId(str: String): Either[Long, String] = {
          if (str.startsWith("\u2668")) Left(str.substring(1).toLong)
          else Right(str)
        }
        def parseResult(jValue: JValue): JValue = jValue match {
          case JObject(jFields) =>
            val replaced = jFields map {
              case field @ JField("execId", JString(str)) =>
                parseId(str) match {
                  case Right(strId) => field.copy(value = JString(strId))
                  case Left(longId) => field.copy(value = JNumber(longId))
                }
              case other =>
                other
            }
            JObject(replaced)
          case other =>
            other
        }
        builder.beginObject()
        builder.addField("jsonrpc", obj.jsonrpc)
        parseId(obj.id) match {
          case Right(strId) => builder.addField("id", strId)
          case Left(longId) => builder.addField("id", longId)
        }
        builder.addField("result", obj.result map parseResult)
        builder.addField("error", obj.error)
        builder.endObject()
      }
    }
}
