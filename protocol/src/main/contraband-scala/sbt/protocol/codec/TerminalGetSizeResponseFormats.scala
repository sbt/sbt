/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait TerminalGetSizeResponseFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val TerminalGetSizeResponseFormat: JsonFormat[sbt.protocol.TerminalGetSizeResponse] = new JsonFormat[sbt.protocol.TerminalGetSizeResponse] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.protocol.TerminalGetSizeResponse = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val width = unbuilder.readField[Int]("width")
      val height = unbuilder.readField[Int]("height")
      unbuilder.endObject()
      sbt.protocol.TerminalGetSizeResponse(width, height)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.protocol.TerminalGetSizeResponse, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("width", obj.width)
    builder.addField("height", obj.height)
    builder.endObject()
  }
}
}
