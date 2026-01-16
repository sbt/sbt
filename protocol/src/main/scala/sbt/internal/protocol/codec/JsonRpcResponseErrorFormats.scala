/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.protocol.codec

import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
import sjsonnew.shaded.scalajson.ast.unsafe.JValue

trait JsonRpcResponseErrorFormats {
  self: sbt.internal.util.codec.JValueFormats & sjsonnew.BasicJsonProtocol =>
  given JsonRpcResponseErrorFormat: JsonFormat[sbt.internal.protocol.JsonRpcResponseError] =
    new JsonFormat[sbt.internal.protocol.JsonRpcResponseError] {
      override def read[J](
          jsOpt: Option[J],
          unbuilder: Unbuilder[J]
      ): sbt.internal.protocol.JsonRpcResponseError = {
        jsOpt match {
          case Some(js) =>
            unbuilder.beginObject(js)
            val code = unbuilder.readField[Long]("code")
            val message = unbuilder.readField[String]("message")
            val data = unbuilder.lookupField("data") map { case x: JValue =>
              x
            }
            unbuilder.endObject()
            sbt.internal.protocol.JsonRpcResponseError(code, message, data)
          case None =>
            deserializationError("Expected JsObject but found None")
        }
      }
      override def write[J](
          obj: sbt.internal.protocol.JsonRpcResponseError,
          builder: Builder[J]
      ): Unit = {
        builder.beginObject()
        builder.addField("code", obj.code)
        builder.addField("message", obj.message)
        builder.addField("data", obj.data)
        builder.endObject()
      }
    }
}
