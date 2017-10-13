/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait PositionFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val PositionFormat: JsonFormat[sbt.internal.langserver.Position] = new JsonFormat[sbt.internal.langserver.Position] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.langserver.Position = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val line = unbuilder.readField[Long]("line")
      val character = unbuilder.readField[Long]("character")
      unbuilder.endObject()
      sbt.internal.langserver.Position(line, character)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.langserver.Position, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("line", obj.line)
    builder.addField("character", obj.character)
    builder.endObject()
  }
}
}
