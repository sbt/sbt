/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait TerminalCapabilitiesResponseFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val TerminalCapabilitiesResponseFormat: JsonFormat[sbt.protocol.TerminalCapabilitiesResponse] = new JsonFormat[sbt.protocol.TerminalCapabilitiesResponse] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.protocol.TerminalCapabilitiesResponse = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val boolean = unbuilder.readField[Option[Boolean]]("boolean")
      val numeric = unbuilder.readField[Option[Int]]("numeric")
      val string = unbuilder.readField[Option[String]]("string")
      unbuilder.endObject()
      sbt.protocol.TerminalCapabilitiesResponse(boolean, numeric, string)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.protocol.TerminalCapabilitiesResponse, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("boolean", obj.boolean)
    builder.addField("numeric", obj.numeric)
    builder.addField("string", obj.string)
    builder.endObject()
  }
}
}
