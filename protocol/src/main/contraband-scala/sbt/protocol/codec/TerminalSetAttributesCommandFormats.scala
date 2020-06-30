/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait TerminalSetAttributesCommandFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val TerminalSetAttributesCommandFormat: JsonFormat[sbt.protocol.TerminalSetAttributesCommand] = new JsonFormat[sbt.protocol.TerminalSetAttributesCommand] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.protocol.TerminalSetAttributesCommand = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val iflag = unbuilder.readField[String]("iflag")
      val oflag = unbuilder.readField[String]("oflag")
      val cflag = unbuilder.readField[String]("cflag")
      val lflag = unbuilder.readField[String]("lflag")
      val cchars = unbuilder.readField[String]("cchars")
      unbuilder.endObject()
      sbt.protocol.TerminalSetAttributesCommand(iflag, oflag, cflag, lflag, cchars)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.protocol.TerminalSetAttributesCommand, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("iflag", obj.iflag)
    builder.addField("oflag", obj.oflag)
    builder.addField("cflag", obj.cflag)
    builder.addField("lflag", obj.lflag)
    builder.addField("cchars", obj.cchars)
    builder.endObject()
  }
}
}
