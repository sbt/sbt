/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait TerminalSetSizeCommandFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val TerminalSetSizeCommandFormat: JsonFormat[sbt.protocol.TerminalSetSizeCommand] = new JsonFormat[sbt.protocol.TerminalSetSizeCommand] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.protocol.TerminalSetSizeCommand = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val width = unbuilder.readField[Int]("width")
      val height = unbuilder.readField[Int]("height")
      unbuilder.endObject()
      sbt.protocol.TerminalSetSizeCommand(width, height)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.protocol.TerminalSetSizeCommand, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("width", obj.width)
    builder.addField("height", obj.height)
    builder.endObject()
  }
}
}
