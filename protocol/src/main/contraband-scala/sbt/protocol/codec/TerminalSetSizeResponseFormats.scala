/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait TerminalSetSizeResponseFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val TerminalSetSizeResponseFormat: JsonFormat[sbt.protocol.TerminalSetSizeResponse] = new JsonFormat[sbt.protocol.TerminalSetSizeResponse] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.protocol.TerminalSetSizeResponse = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      
      unbuilder.endObject()
      sbt.protocol.TerminalSetSizeResponse()
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.protocol.TerminalSetSizeResponse, builder: Builder[J]): Unit = {
    builder.beginObject()
    
    builder.endObject()
  }
}
}
