/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait TerminalCapabilitiesQueryFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val TerminalCapabilitiesQueryFormat: JsonFormat[sbt.protocol.TerminalCapabilitiesQuery] = new JsonFormat[sbt.protocol.TerminalCapabilitiesQuery] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.protocol.TerminalCapabilitiesQuery = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val boolean = unbuilder.readField[Option[String]]("boolean")
      val numeric = unbuilder.readField[Option[String]]("numeric")
      val string = unbuilder.readField[Option[String]]("string")
      val jline3 = unbuilder.readField[Boolean]("jline3")
      unbuilder.endObject()
      sbt.protocol.TerminalCapabilitiesQuery(boolean, numeric, string, jline3)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.protocol.TerminalCapabilitiesQuery, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("boolean", obj.boolean)
    builder.addField("numeric", obj.numeric)
    builder.addField("string", obj.string)
    builder.addField("jline3", obj.jline3)
    builder.endObject()
  }
}
}
