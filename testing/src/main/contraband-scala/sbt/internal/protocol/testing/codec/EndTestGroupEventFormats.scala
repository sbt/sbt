/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.protocol.testing.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait EndTestGroupEventFormats { self: sbt.internal.protocol.testing.codec.TestResultFormats with sjsonnew.BasicJsonProtocol =>
implicit lazy val EndTestGroupEventFormat: JsonFormat[sbt.internal.protocol.testing.EndTestGroupEvent] = new JsonFormat[sbt.internal.protocol.testing.EndTestGroupEvent] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.protocol.testing.EndTestGroupEvent = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val name = unbuilder.readField[String]("name")
      val result = unbuilder.readField[sbt.internal.protocol.testing.TestResult]("result")
      unbuilder.endObject()
      sbt.internal.protocol.testing.EndTestGroupEvent(name, result)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.protocol.testing.EndTestGroupEvent, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("name", obj.name)
    builder.addField("result", obj.result)
    builder.endObject()
  }
}
}
