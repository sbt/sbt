/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ExecInfoFormats { self: sjsonnew.BasicJsonProtocol =>
given ExecInfoFormat: JsonFormat[sbt.protocol.ExecInfo] = new JsonFormat[sbt.protocol.ExecInfo] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.protocol.ExecInfo = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val id = unbuilder.readField[String]("id")
      val channelName = unbuilder.readField[String]("channelName")
      val clientName = unbuilder.readField[String]("clientName")
      val commandLine = unbuilder.readField[String]("commandLine")
      val status = unbuilder.readField[String]("status")
      unbuilder.endObject()
      sbt.protocol.ExecInfo(id, channelName, clientName, commandLine, status)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.protocol.ExecInfo, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("id", obj.id)
    builder.addField("channelName", obj.channelName)
    builder.addField("clientName", obj.clientName)
    builder.addField("commandLine", obj.commandLine)
    builder.addField("status", obj.status)
    builder.endObject()
  }
}
}
