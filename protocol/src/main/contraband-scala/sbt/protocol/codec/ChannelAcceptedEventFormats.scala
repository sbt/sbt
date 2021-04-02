/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ChannelAcceptedEventFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val ChannelAcceptedEventFormat: JsonFormat[sbt.protocol.ChannelAcceptedEvent] = new JsonFormat[sbt.protocol.ChannelAcceptedEvent] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.protocol.ChannelAcceptedEvent = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val channelName = unbuilder.readField[String]("channelName")
      unbuilder.endObject()
      sbt.protocol.ChannelAcceptedEvent(channelName)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.protocol.ChannelAcceptedEvent, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("channelName", obj.channelName)
    builder.endObject()
  }
}
}
