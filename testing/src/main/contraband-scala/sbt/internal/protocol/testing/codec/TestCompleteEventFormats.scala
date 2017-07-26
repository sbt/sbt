/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.protocol.testing.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait TestCompleteEventFormats { self: sbt.internal.protocol.testing.codec.TestResultFormats with sjsonnew.BasicJsonProtocol =>
implicit lazy val TestCompleteEventFormat: JsonFormat[sbt.internal.protocol.testing.TestCompleteEvent] = new JsonFormat[sbt.internal.protocol.testing.TestCompleteEvent] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.protocol.testing.TestCompleteEvent = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val result = unbuilder.readField[sbt.internal.protocol.testing.TestResult]("result")
      unbuilder.endObject()
      sbt.internal.protocol.testing.TestCompleteEvent(result)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.protocol.testing.TestCompleteEvent, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("result", obj.result)
    builder.endObject()
  }
}
}
