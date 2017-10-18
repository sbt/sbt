/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.internal.protocol.codec

import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
import sjsonnew.shaded.scalajson.ast.unsafe.JValue

trait JsonRpcResponseMessageFormats {
  self: sbt.internal.util.codec.JValueFormats
    with sbt.internal.protocol.codec.JsonRpcResponseErrorFormats
    with sjsonnew.BasicJsonProtocol =>
  implicit lazy val JsonRpcResponseMessageFormat
    : JsonFormat[sbt.internal.protocol.JsonRpcResponseMessage] =
    new JsonFormat[sbt.internal.protocol.JsonRpcResponseMessage] {
      override def read[J](
          jsOpt: Option[J],
          unbuilder: Unbuilder[J]): sbt.internal.protocol.JsonRpcResponseMessage = {
        jsOpt match {
          case Some(js) =>
            unbuilder.beginObject(js)
            val jsonrpc = unbuilder.readField[String]("jsonrpc")
            val id = try {
              unbuilder.readField[Option[String]]("id")
            } catch {
              case _: Throwable => unbuilder.readField[Option[Long]]("id") map { _.toString }
            }

            val result = unbuilder.lookupField("result") map {
              case x: JValue => x
            }

            val error =
              unbuilder.readField[Option[sbt.internal.protocol.JsonRpcResponseError]]("error")

            unbuilder.endObject()
            sbt.internal.protocol.JsonRpcResponseMessage(jsonrpc, id, result, error)
          case None =>
            deserializationError("Expected JsObject but found None")
        }
      }
      override def write[J](obj: sbt.internal.protocol.JsonRpcResponseMessage,
                            builder: Builder[J]): Unit = {
        builder.beginObject()
        builder.addField("jsonrpc", obj.jsonrpc)
        builder.addField("id", obj.id)
        builder.addField("result", obj.result)
        builder.addField("error", obj.error)
        builder.endObject()
      }
    }
}
