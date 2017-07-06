/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.testing.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait EndTestGroupErrorEventFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val EndTestGroupErrorEventFormat: JsonFormat[sbt.protocol.testing.EndTestGroupErrorEvent] = new JsonFormat[sbt.protocol.testing.EndTestGroupErrorEvent] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.protocol.testing.EndTestGroupErrorEvent = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val name = unbuilder.readField[String]("name")
      val error = unbuilder.readField[String]("error")
      unbuilder.endObject()
      sbt.protocol.testing.EndTestGroupErrorEvent(name, error)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.protocol.testing.EndTestGroupErrorEvent, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("name", obj.name)
    builder.addField("error", obj.error)
    builder.endObject()
  }
}
}
