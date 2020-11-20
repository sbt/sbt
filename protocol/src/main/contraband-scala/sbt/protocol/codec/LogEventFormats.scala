/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait LogEventFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val LogEventFormat: JsonFormat[sbt.protocol.LogEvent] = new JsonFormat[sbt.protocol.LogEvent] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.protocol.LogEvent = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val level = unbuilder.readField[String]("level")
      val message = unbuilder.readField[String]("message")
      unbuilder.endObject()
      sbt.protocol.LogEvent(level, message)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.protocol.LogEvent, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("level", obj.level)
    builder.addField("message", obj.message)
    builder.endObject()
  }
}
}
