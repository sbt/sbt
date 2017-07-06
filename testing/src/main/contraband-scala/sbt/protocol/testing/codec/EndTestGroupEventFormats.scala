/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.testing.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait EndTestGroupEventFormats { self: sbt.protocol.testing.codec.TestResultFormats with sjsonnew.BasicJsonProtocol =>
implicit lazy val EndTestGroupEventFormat: JsonFormat[sbt.protocol.testing.EndTestGroupEvent] = new JsonFormat[sbt.protocol.testing.EndTestGroupEvent] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.protocol.testing.EndTestGroupEvent = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val name = unbuilder.readField[String]("name")
      val result = unbuilder.readField[sbt.protocol.testing.TestResult]("result")
      unbuilder.endObject()
      sbt.protocol.testing.EndTestGroupEvent(name, result)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.protocol.testing.EndTestGroupEvent, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("name", obj.name)
    builder.addField("result", obj.result)
    builder.endObject()
  }
}
}
