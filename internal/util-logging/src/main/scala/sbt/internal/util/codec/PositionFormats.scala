/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util.codec

import sjsonnew.{ deserializationError, Builder, JsonFormat, Unbuilder }
import xsbti.Position
import java.util.Optional

trait PositionFormats { self: sjsonnew.BasicJsonProtocol =>
  given PositionFormat: JsonFormat[Position] = new JsonFormat[Position] {
    override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): Position = {
      jsOpt match {
        case Some(js) =>
          unbuilder.beginObject(js)
          val line0 = unbuilder.readField[Optional[java.lang.Integer]]("line")
          val lineContent0 = unbuilder.readField[String]("lineContent")
          val offset0 = unbuilder.readField[Optional[java.lang.Integer]]("offset")
          val pointer0 = unbuilder.readField[Optional[java.lang.Integer]]("pointer")
          val pointerSpace0 = unbuilder.readField[Optional[String]]("pointerSpace")
          val sourcePath0 = unbuilder.readField[Optional[String]]("sourcePath")
          val sourceFile0 = unbuilder.readField[Optional[java.io.File]]("sourceFile")
          val startOffset0 = unbuilder.readField[Optional[java.lang.Integer]]("startOffset")
          val endOffset0 = unbuilder.readField[Optional[java.lang.Integer]]("endOffset")
          val startLine0 = unbuilder.readField[Optional[java.lang.Integer]]("startLine")
          val startColumn0 = unbuilder.readField[Optional[java.lang.Integer]]("startColumn")
          val endLine0 = unbuilder.readField[Optional[java.lang.Integer]]("endLine")
          val endColumn0 = unbuilder.readField[Optional[java.lang.Integer]]("endColumn")

          unbuilder.endObject()
          new Position() {
            override val line = line0
            override val lineContent = lineContent0
            override val offset = offset0
            override val pointer = pointer0
            override val pointerSpace = pointerSpace0
            override val sourcePath = sourcePath0
            override val sourceFile = sourceFile0
            override val startOffset = startOffset0
            override val endOffset = endOffset0
            override val startLine = startLine0
            override val startColumn = startColumn0
            override val endLine = endLine0
            override val endColumn = endColumn0

          }
        case None =>
          deserializationError("Expected JsObject but found None")
      }
    }
    override def write[J](obj: Position, builder: Builder[J]): Unit = {
      builder.beginObject()
      builder.addField("line", obj.line)
      builder.addField("lineContent", obj.lineContent)
      builder.addField("offset", obj.offset)
      builder.addField("pointer", obj.pointer)
      builder.addField("pointerSpace", obj.pointerSpace)
      builder.addField("sourcePath", obj.sourcePath)
      builder.addField("sourceFile", obj.sourceFile)
      builder.addField("startOffset", obj.startOffset)
      builder.addField("endOffset", obj.endOffset)
      builder.addField("startLine", obj.startLine)
      builder.addField("startColumn", obj.startColumn)
      builder.addField("endLine", obj.endLine)
      builder.addField("endColumn", obj.endColumn)

      builder.endObject()
    }
  }
}
