/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util.codec

import xsbti.{ Problem, Severity, Position }
import _root_.sjsonnew.{ deserializationError, Builder, JsonFormat, Unbuilder }
import java.util.Optional

trait ProblemFormats { self: SeverityFormats & PositionFormats & sjsonnew.BasicJsonProtocol =>
  given ProblemFormat: JsonFormat[Problem] = new JsonFormat[Problem] {
    override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): Problem = {
      jsOpt match {
        case Some(js) =>
          unbuilder.beginObject(js)
          val category0 = unbuilder.readField[String]("category")
          val severity0 = unbuilder.readField[Severity]("severity")
          val message0 = unbuilder.readField[String]("message")
          val position0 = unbuilder.readField[Position]("position")
          val rendered0 = unbuilder.readField[Optional[String]]("rendered")

          unbuilder.endObject()
          new Problem {
            override val category = category0
            override val position = position0
            override val message = message0
            override val severity = severity0
            override val rendered = rendered0
          }
        case None =>
          deserializationError("Expected JsObject but found None")
      }
    }
    override def write[J](obj: Problem, builder: Builder[J]): Unit = {
      builder.beginObject()
      builder.addField("category", obj.category)
      builder.addField("severity", obj.severity)
      builder.addField("message", obj.message)
      builder.addField("position", obj.position)
      builder.addField("rendered", obj.rendered)
      builder.endObject()
    }
  }
}
