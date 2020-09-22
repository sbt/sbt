/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait TerminalSetEchoCommandFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val TerminalSetEchoCommandFormat: JsonFormat[sbt.protocol.TerminalSetEchoCommand] = new JsonFormat[sbt.protocol.TerminalSetEchoCommand] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.protocol.TerminalSetEchoCommand = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val toggle = unbuilder.readField[Boolean]("toggle")
      unbuilder.endObject()
      sbt.protocol.TerminalSetEchoCommand(toggle)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.protocol.TerminalSetEchoCommand, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("toggle", obj.toggle)
    builder.endObject()
  }
}
}
