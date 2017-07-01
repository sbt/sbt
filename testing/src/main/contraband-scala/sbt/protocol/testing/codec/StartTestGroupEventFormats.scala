/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.testing.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait StartTestGroupEventFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val StartTestGroupEventFormat: JsonFormat[sbt.protocol.testing.StartTestGroupEvent] = new JsonFormat[sbt.protocol.testing.StartTestGroupEvent] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.protocol.testing.StartTestGroupEvent = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
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
