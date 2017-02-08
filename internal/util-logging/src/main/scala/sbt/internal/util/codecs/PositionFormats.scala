/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

package sbt.internal.util.codec
import _root_.sjsonnew.{ deserializationError, Builder, JsonFormat, Unbuilder }
import xsbti.Position
import java.util.Optional

trait PositionFormats { self: sjsonnew.BasicJsonProtocol =>
  implicit lazy val PositionFormat: JsonFormat[Position] = new JsonFormat[Position] {
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
          unbuilder.endObject()
          new Position() {
            override val line = line0
            override val lineContent = lineContent0
            override val offset = offset0
            override val pointer = pointer0
            override val pointerSpace = pointerSpace0
            override val sourcePath = sourcePath0
            override val sourceFile = sourceFile0
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
      builder.endObject()
    }
  }
}
