/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.testing.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait TestStringEventFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val TestStringEventFormat: JsonFormat[sbt.protocol.testing.TestStringEvent] = new JsonFormat[sbt.protocol.testing.TestStringEvent] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.protocol.testing.TestStringEvent = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val value = unbuilder.readField[String]("value")
      unbuilder.endObject()
      sbt.protocol.testing.TestStringEvent(value)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.protocol.testing.TestStringEvent, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("value", obj.value)
    builder.endObject()
  }
}
}
