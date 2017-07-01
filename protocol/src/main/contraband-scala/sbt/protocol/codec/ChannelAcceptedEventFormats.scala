/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ChannelAcceptedEventFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val ChannelAcceptedEventFormat: JsonFormat[sbt.protocol.ChannelAcceptedEvent] = new JsonFormat[sbt.protocol.ChannelAcceptedEvent] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.protocol.ChannelAcceptedEvent = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
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
