/**
 * This code is generated using sbt-datatype.
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.codec
import _root_.sjsonnew.{ deserializationError, serializationError, Builder, JsonFormat, Unbuilder }
trait ExecStatusEventFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val ExecStatusEventFormat: JsonFormat[sbt.protocol.ExecStatusEvent] = new JsonFormat[sbt.protocol.ExecStatusEvent] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.protocol.ExecStatusEvent = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val status = unbuilder.readField[String]("status")
      val commandQueue = unbuilder.readField[Vector[String]]("commandQueue")
      unbuilder.endObject()
      sbt.protocol.ExecStatusEvent(status, commandQueue)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.protocol.ExecStatusEvent, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("status", obj.status)
    builder.addField("commandQueue", obj.commandQueue)
    builder.endObject()
  }
}
}
