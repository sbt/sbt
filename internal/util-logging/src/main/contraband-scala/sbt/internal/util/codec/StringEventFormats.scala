/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.util.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait StringEventFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val StringEventFormat: JsonFormat[sbt.internal.util.StringEvent] = new JsonFormat[sbt.internal.util.StringEvent] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.util.StringEvent = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val level = unbuilder.readField[String]("level")
      val message = unbuilder.readField[String]("message")
      val channelName = unbuilder.readField[Option[String]]("channelName")
      val execId = unbuilder.readField[Option[String]]("execId")
      unbuilder.endObject()
      sbt.internal.util.StringEvent(level, message, channelName, execId)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.util.StringEvent, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("level", obj.level)
    builder.addField("message", obj.message)
    builder.addField("channelName", obj.channelName)
    builder.addField("execId", obj.execId)
    builder.endObject()
  }
}
}
