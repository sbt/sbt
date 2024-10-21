/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait CommandSourceFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val CommandSourceFormat: JsonFormat[sbt.CommandSource] = new JsonFormat[sbt.CommandSource] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.CommandSource = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val channelName = unbuilder.readField[String]("channelName")
      unbuilder.endObject()
      sbt.CommandSource(channelName)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.CommandSource, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("channelName", obj.channelName)
    builder.endObject()
  }
}
}
