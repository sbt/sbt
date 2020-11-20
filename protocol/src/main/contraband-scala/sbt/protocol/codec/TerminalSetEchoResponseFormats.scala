/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait TerminalSetEchoResponseFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val TerminalSetEchoResponseFormat: JsonFormat[sbt.protocol.TerminalSetEchoResponse] = new JsonFormat[sbt.protocol.TerminalSetEchoResponse] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.protocol.TerminalSetEchoResponse = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      
      unbuilder.endObject()
      sbt.protocol.TerminalSetEchoResponse()
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.protocol.TerminalSetEchoResponse, builder: Builder[J]): Unit = {
    builder.beginObject()
    
    builder.endObject()
  }
}
}
