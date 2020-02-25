/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ExecStatusEventFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val ExecStatusEventFormat: JsonFormat[sbt.protocol.ExecStatusEvent] = new JsonFormat[sbt.protocol.ExecStatusEvent] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.protocol.ExecStatusEvent = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val status = unbuilder.readField[String]("status")
      val channelName = unbuilder.readField[Option[String]]("channelName")
      val execId = unbuilder.readField[Option[String]]("execId")
      val commandQueue = unbuilder.readField[Vector[String]]("commandQueue")
      val exitCode = unbuilder.readField[Option[Long]]("exitCode")
      val message = unbuilder.readField[Option[String]]("message")
      unbuilder.endObject()
      sbt.protocol.ExecStatusEvent(status, channelName, execId, commandQueue, exitCode, message)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.protocol.ExecStatusEvent, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("status", obj.status)
    builder.addField("channelName", obj.channelName)
    builder.addField("execId", obj.execId)
    builder.addField("commandQueue", obj.commandQueue)
    builder.addField("exitCode", obj.exitCode)
    builder.addField("message", obj.message)
    builder.endObject()
  }
}
}
