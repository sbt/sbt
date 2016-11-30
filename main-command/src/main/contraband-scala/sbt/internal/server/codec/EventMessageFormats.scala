/**
 * This code is generated using sbt-datatype.
 */

// DO NOT EDIT MANUALLY
package sbt.internal.server.codec
import _root_.sjsonnew.{ deserializationError, serializationError, Builder, JsonFormat, Unbuilder }
trait EventMessageFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val EventMessageFormat: JsonFormat[sbt.internal.server.EventMessage] = new JsonFormat[sbt.internal.server.EventMessage] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.server.EventMessage = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val `type` = unbuilder.readField[String]("type")
      val status = unbuilder.readField[Option[String]]("status")
      val commandQueue = unbuilder.readField[scala.Vector[String]]("commandQueue")
      val level = unbuilder.readField[Option[String]]("level")
      val message = unbuilder.readField[Option[String]]("message")
      val success = unbuilder.readField[Option[Boolean]]("success")
      val commandLine = unbuilder.readField[Option[String]]("commandLine")
      unbuilder.endObject()
      sbt.internal.server.EventMessage(`type`, status, commandQueue, level, message, success, commandLine)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.server.EventMessage, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("type", obj.`type`)
    builder.addField("status", obj.status)
    builder.addField("commandQueue", obj.commandQueue)
    builder.addField("level", obj.level)
    builder.addField("message", obj.message)
    builder.addField("success", obj.success)
    builder.addField("commandLine", obj.commandLine)
    builder.endObject()
  }
}
}
