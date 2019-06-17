/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

import scala.concurrent.duration.FiniteDuration
import sjsonnew._
import sjsonnew.BasicJsonProtocol._

object FiniteDurationCodec {
  implicit val finiteDurationJsonFormat: JsonFormat[FiniteDuration] =
    new JsonFormat[FiniteDuration] {
      override def write[J](obj: FiniteDuration, builder: Builder[J]): Unit = {
        builder.beginObject()
        builder.addField("length", obj.length)
        builder.addField("unit", obj.unit.name)
        builder.endObject()
      }

      override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): FiniteDuration = {
        jsOpt match {
          case None => deserializationError("Expected JsObject but found None")
          case Some(js) =>
            unbuilder.beginObject(js)
            val length = unbuilder.readField[Long]("length")
            val unit = unbuilder.readField[String]("unit")
            unbuilder.endObject()
            FiniteDuration(length, unit)
        }
      }
    }
}
