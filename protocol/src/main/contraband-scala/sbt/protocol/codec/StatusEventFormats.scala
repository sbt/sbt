/**
 * This code is generated using sbt-datatype.
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.codec
import _root_.sjsonnew.{ deserializationError, serializationError, Builder, JsonFormat, Unbuilder }
trait StatusEventFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val StatusEventFormat: JsonFormat[sbt.protocol.StatusEvent] = new JsonFormat[sbt.protocol.StatusEvent] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.protocol.StatusEvent = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val status = unbuilder.readField[String]("status")
      val commandQueue = unbuilder.readField[Vector[String]]("commandQueue")
      unbuilder.endObject()
      sbt.protocol.StatusEvent(status, commandQueue)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.protocol.StatusEvent, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("status", obj.status)
    builder.addField("commandQueue", obj.commandQueue)
    builder.endObject()
  }
}
}
