/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.util.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ProgressEventFormats { self: sbt.internal.util.codec.ProgressItemFormats & sjsonnew.BasicJsonProtocol =>
implicit lazy val ProgressEventFormat: JsonFormat[sbt.internal.util.ProgressEvent] = new JsonFormat[sbt.internal.util.ProgressEvent] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.util.ProgressEvent = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val level = unbuilder.readField[String]("level")
      val items = unbuilder.readField[Vector[sbt.internal.util.ProgressItem]]("items")
      val lastTaskCount = unbuilder.readField[Option[Int]]("lastTaskCount")
      val channelName = unbuilder.readField[Option[String]]("channelName")
      val execId = unbuilder.readField[Option[String]]("execId")
      val command = unbuilder.readField[Option[String]]("command")
      val skipIfActive = unbuilder.readField[Option[Boolean]]("skipIfActive")
      unbuilder.endObject()
      sbt.internal.util.ProgressEvent(level, items, lastTaskCount, channelName, execId, command, skipIfActive)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.util.ProgressEvent, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("level", obj.level)
    builder.addField("items", obj.items)
    builder.addField("lastTaskCount", obj.lastTaskCount)
    builder.addField("channelName", obj.channelName)
    builder.addField("execId", obj.execId)
    builder.addField("command", obj.command)
    builder.addField("skipIfActive", obj.skipIfActive)
    builder.endObject()
  }
}
}
