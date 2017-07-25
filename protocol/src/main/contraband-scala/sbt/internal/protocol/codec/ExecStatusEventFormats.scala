/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.protocol.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ExecStatusEventFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val ExecStatusEventFormat: JsonFormat[sbt.internal.protocol.ExecStatusEvent] = new JsonFormat[sbt.internal.protocol.ExecStatusEvent] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.protocol.ExecStatusEvent = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val status = unbuilder.readField[String]("status")
      val channelName = unbuilder.readField[Option[String]]("channelName")
      val execId = unbuilder.readField[Option[String]]("execId")
      val commandQueue = unbuilder.readField[Vector[String]]("commandQueue")
      unbuilder.endObject()
      sbt.internal.protocol.ExecStatusEvent(status, channelName, execId, commandQueue)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.protocol.ExecStatusEvent, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("status", obj.status)
    builder.addField("channelName", obj.channelName)
    builder.addField("execId", obj.execId)
    builder.addField("commandQueue", obj.commandQueue)
    builder.endObject()
  }
}
}
