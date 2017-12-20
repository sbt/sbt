/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.internal.protocol.codec

import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
import sjsonnew.shaded.scalajson.ast.unsafe.JValue

trait JsonRpcNotificationMessageFormats {
  self: sbt.internal.util.codec.JValueFormats with sjsonnew.BasicJsonProtocol =>
  implicit lazy val JsonRpcNotificationMessageFormat
    : JsonFormat[sbt.internal.protocol.JsonRpcNotificationMessage] =
    new JsonFormat[sbt.internal.protocol.JsonRpcNotificationMessage] {
      override def read[J](
          jsOpt: Option[J],
          unbuilder: Unbuilder[J]): sbt.internal.protocol.JsonRpcNotificationMessage = {
        jsOpt match {
          case Some(js) =>
            unbuilder.beginObject(js)
            val jsonrpc = unbuilder.readField[String]("jsonrpc")
            val method = unbuilder.readField[String]("method")
            val params = unbuilder.lookupField("params") map {
              case x: JValue => x
            }
            unbuilder.endObject()
            sbt.internal.protocol.JsonRpcNotificationMessage(jsonrpc, method, params)
          case None =>
            deserializationError("Expected JsObject but found None")
        }
      }
      override def write[J](obj: sbt.internal.protocol.JsonRpcNotificationMessage,
                            builder: Builder[J]): Unit = {
        builder.beginObject()
        builder.addField("jsonrpc", obj.jsonrpc)
        builder.addField("method", obj.method)
        builder.addField("params", obj.params)
        builder.endObject()
      }
    }
}
