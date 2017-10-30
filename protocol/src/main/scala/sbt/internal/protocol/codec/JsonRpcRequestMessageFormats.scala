/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.internal.protocol.codec

import sjsonnew.shaded.scalajson.ast.unsafe.JValue
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }

trait JsonRpcRequestMessageFormats {
  self: sbt.internal.util.codec.JValueFormats with sjsonnew.BasicJsonProtocol =>
  implicit lazy val JsonRpcRequestMessageFormat
    : JsonFormat[sbt.internal.protocol.JsonRpcRequestMessage] =
    new JsonFormat[sbt.internal.protocol.JsonRpcRequestMessage] {
      override def read[J](jsOpt: Option[J],
                           unbuilder: Unbuilder[J]): sbt.internal.protocol.JsonRpcRequestMessage = {
        jsOpt match {
          case Some(js) =>
            unbuilder.beginObject(js)
            val jsonrpc = unbuilder.readField[String]("jsonrpc")
            val id = try {
              unbuilder.readField[String]("id")
            } catch {
              case _: Throwable => unbuilder.readField[Long]("id").toString
            }
            val method = unbuilder.readField[String]("method")
            val params = unbuilder.lookupField("params") map {
              case x: JValue => x
            }
            unbuilder.endObject()
            sbt.internal.protocol.JsonRpcRequestMessage(jsonrpc, id, method, params)
          case None =>
            deserializationError("Expected JsObject but found None")
        }
      }
      override def write[J](obj: sbt.internal.protocol.JsonRpcRequestMessage,
                            builder: Builder[J]): Unit = {
        builder.beginObject()
        builder.addField("jsonrpc", obj.jsonrpc)
        builder.addField("id", obj.id)
        builder.addField("method", obj.method)
        builder.addField("params", obj.params)
        builder.endObject()
      }
    }
}
