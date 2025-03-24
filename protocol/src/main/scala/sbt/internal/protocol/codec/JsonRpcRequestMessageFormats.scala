/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.protocol.codec

import sjsonnew.shaded.scalajson.ast.unsafe.JValue
import sjsonnew.{ Builder, DeserializationException, JsonFormat, Unbuilder, deserializationError }

trait JsonRpcRequestMessageFormats {
  self: sbt.internal.util.codec.JValueFormats & sjsonnew.BasicJsonProtocol =>
  given JsonRpcRequestMessageFormat: JsonFormat[sbt.internal.protocol.JsonRpcRequestMessage] =
    new JsonFormat[sbt.internal.protocol.JsonRpcRequestMessage] {
      override def read[J](
          jsOpt: Option[J],
          unbuilder: Unbuilder[J]
      ): sbt.internal.protocol.JsonRpcRequestMessage = {
        jsOpt match {
          case Some(js) =>
            unbuilder.beginObject(js)
            val jsonrpc = unbuilder.readField[String]("jsonrpc")
            val id =
              try {
                unbuilder.readField[String]("id")
              } catch {
                case _: DeserializationException => {
                  val prefix = "\u2668" // Append prefix to show the original type was Number
                  prefix + unbuilder.readField[Long]("id").toString
                }
              }
            val method = unbuilder.readField[String]("method")
            val params = unbuilder.lookupField("params") map { case x: JValue =>
              x
            }
            unbuilder.endObject()
            sbt.internal.protocol.JsonRpcRequestMessage(jsonrpc, id, method, params)
          case None =>
            deserializationError("Expected JsObject but found None")
        }
      }
      override def write[J](
          obj: sbt.internal.protocol.JsonRpcRequestMessage,
          builder: Builder[J]
      ): Unit = {
        builder.beginObject()
        builder.addField("jsonrpc", obj.jsonrpc)
        builder.addField("id", obj.id)
        builder.addField("method", obj.method)
        builder.addField("params", obj.params)
        builder.endObject()
      }
    }
}
