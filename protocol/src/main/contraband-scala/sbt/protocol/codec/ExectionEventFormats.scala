/**
 * This code is generated using sbt-datatype.
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.codec
import _root_.sjsonnew.{ deserializationError, serializationError, Builder, JsonFormat, Unbuilder }
trait ExectionEventFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val ExectionEventFormat: JsonFormat[sbt.protocol.ExectionEvent] = new JsonFormat[sbt.protocol.ExectionEvent] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.protocol.ExectionEvent = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val success = unbuilder.readField[String]("success")
      val commandLine = unbuilder.readField[String]("commandLine")
      unbuilder.endObject()
      sbt.protocol.ExectionEvent(success, commandLine)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.protocol.ExectionEvent, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("success", obj.success)
    builder.addField("commandLine", obj.commandLine)
    builder.endObject()
  }
}
}
