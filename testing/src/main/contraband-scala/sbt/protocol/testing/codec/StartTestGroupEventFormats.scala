/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.testing.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait StartTestGroupEventFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val StartTestGroupEventFormat: JsonFormat[sbt.protocol.testing.StartTestGroupEvent] = new JsonFormat[sbt.protocol.testing.StartTestGroupEvent] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.protocol.testing.StartTestGroupEvent = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val name = unbuilder.readField[String]("name")
      unbuilder.endObject()
      sbt.protocol.testing.StartTestGroupEvent(name)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.protocol.testing.StartTestGroupEvent, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("name", obj.name)
    builder.endObject()
  }
}
}
