/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait PositionFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val PositionFormat: JsonFormat[sbt.internal.bsp.Position] = new JsonFormat[sbt.internal.bsp.Position] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.Position = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val line = unbuilder.readField[Long]("line")
      val character = unbuilder.readField[Long]("character")
      unbuilder.endObject()
      sbt.internal.bsp.Position(line, character)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.Position, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("line", obj.line)
    builder.addField("character", obj.character)
    builder.endObject()
  }
}
}
