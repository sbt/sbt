/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.protocol.testing.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait TestStringEventFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val TestStringEventFormat: JsonFormat[sbt.internal.protocol.testing.TestStringEvent] = new JsonFormat[sbt.internal.protocol.testing.TestStringEvent] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.protocol.testing.TestStringEvent = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val value = unbuilder.readField[String]("value")
      unbuilder.endObject()
      sbt.internal.protocol.testing.TestStringEvent(value)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.protocol.testing.TestStringEvent, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("value", obj.value)
    builder.endObject()
  }
}
}
