/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.testing.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait TestItemEventFormats { self: sbt.protocol.testing.codec.TestResultFormats & sjsonnew.BasicJsonProtocol & sbt.protocol.testing.codec.TestItemDetailFormats & sbt.internal.testing.StatusFormats =>
implicit lazy val TestItemEventFormat: JsonFormat[sbt.protocol.testing.TestItemEvent] = new JsonFormat[sbt.protocol.testing.TestItemEvent] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.protocol.testing.TestItemEvent = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val result = unbuilder.readField[Option[sbt.protocol.testing.TestResult]]("result")
      val detail = unbuilder.readField[Vector[sbt.protocol.testing.TestItemDetail]]("detail")
      unbuilder.endObject()
      sbt.protocol.testing.TestItemEvent(result, detail)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.protocol.testing.TestItemEvent, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("result", obj.result)
    builder.addField("detail", obj.detail)
    builder.endObject()
  }
}
}
