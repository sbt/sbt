/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait CommandSourceFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val CommandSourceFormat: JsonFormat[sbt.CommandSource] = new JsonFormat[sbt.CommandSource] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.CommandSource = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
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
