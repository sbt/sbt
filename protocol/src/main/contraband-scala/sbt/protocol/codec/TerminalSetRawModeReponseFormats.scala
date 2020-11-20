/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait TerminalSetRawModeReponseFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val TerminalSetRawModeReponseFormat: JsonFormat[sbt.protocol.TerminalSetRawModeReponse] = new JsonFormat[sbt.protocol.TerminalSetRawModeReponse] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.protocol.TerminalSetRawModeReponse = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      
      unbuilder.endObject()
      sbt.protocol.TerminalSetRawModeReponse()
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.protocol.TerminalSetRawModeReponse, builder: Builder[J]): Unit = {
    builder.beginObject()
    
    builder.endObject()
  }
}
}
