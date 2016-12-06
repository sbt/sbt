/**
 * This code is generated using sbt-datatype.
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.codec
import _root_.sjsonnew.{ deserializationError, serializationError, Builder, JsonFormat, Unbuilder }
trait ConsolePromptEventFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val ConsolePromptEventFormat: JsonFormat[sbt.protocol.ConsolePromptEvent] = new JsonFormat[sbt.protocol.ConsolePromptEvent] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.protocol.ConsolePromptEvent = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      
      unbuilder.endObject()
      sbt.protocol.ConsolePromptEvent()
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.protocol.ConsolePromptEvent, builder: Builder[J]): Unit = {
    builder.beginObject()
    
    builder.endObject()
  }
}
}
