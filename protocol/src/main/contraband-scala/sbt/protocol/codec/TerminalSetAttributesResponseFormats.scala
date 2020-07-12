/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait TerminalSetAttributesResponseFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val TerminalSetAttributesResponseFormat: JsonFormat[sbt.protocol.TerminalSetAttributesResponse] = new JsonFormat[sbt.protocol.TerminalSetAttributesResponse] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.protocol.TerminalSetAttributesResponse = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      
      unbuilder.endObject()
      sbt.protocol.TerminalSetAttributesResponse()
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.protocol.TerminalSetAttributesResponse, builder: Builder[J]): Unit = {
    builder.beginObject()
    
    builder.endObject()
  }
}
}
