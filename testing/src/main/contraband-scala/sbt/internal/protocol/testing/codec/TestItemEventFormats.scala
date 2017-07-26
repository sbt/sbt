/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.protocol.testing.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait TestItemEventFormats { self: sbt.internal.protocol.testing.codec.TestResultFormats with sbt.internal.protocol.testing.codec.TestItemDetailFormats with sjsonnew.BasicJsonProtocol =>
implicit lazy val TestItemEventFormat: JsonFormat[sbt.internal.protocol.testing.TestItemEvent] = new JsonFormat[sbt.internal.protocol.testing.TestItemEvent] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.protocol.testing.TestItemEvent = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val result = unbuilder.readField[Option[sbt.internal.protocol.testing.TestResult]]("result")
      val detail = unbuilder.readField[Vector[sbt.internal.protocol.testing.TestItemDetail]]("detail")
      unbuilder.endObject()
      sbt.internal.protocol.testing.TestItemEvent(result, detail)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.protocol.testing.TestItemEvent, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("result", obj.result)
    builder.addField("detail", obj.detail)
    builder.endObject()
  }
}
}
