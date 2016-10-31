/**
 * This code is generated using sbt-datatype.
 */

// DO NOT EDIT MANUALLY
package sbt.internal.server.codec
import _root_.sjsonnew.{ deserializationError, serializationError, Builder, JsonFormat, Unbuilder }
trait CommandMessageFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val CommandMessageFormat: JsonFormat[sbt.internal.server.CommandMessage] = new JsonFormat[sbt.internal.server.CommandMessage] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.server.CommandMessage = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val `type` = unbuilder.readField[String]("type")
      val commandLine = unbuilder.readField[Option[String]]("commandLine")
      unbuilder.endObject()
      new sbt.internal.server.CommandMessage(`type`, commandLine)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.server.CommandMessage, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("type", obj.`type`)
    builder.addField("commandLine", obj.commandLine)
    builder.endObject()
  }
}
}
