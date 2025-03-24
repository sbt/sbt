/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.testing.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait TestItemDetailFormats { self: sbt.internal.testing.StatusFormats & sjsonnew.BasicJsonProtocol =>
implicit lazy val TestItemDetailFormat: JsonFormat[sbt.protocol.testing.TestItemDetail] = new JsonFormat[sbt.protocol.testing.TestItemDetail] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.protocol.testing.TestItemDetail = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val fullyQualifiedName = unbuilder.readField[String]("fullyQualifiedName")
      val status = unbuilder.readField[sbt.testing.Status]("status")
      val duration = unbuilder.readField[Option[Long]]("duration")
      unbuilder.endObject()
      sbt.protocol.testing.TestItemDetail(fullyQualifiedName, status, duration)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.protocol.testing.TestItemDetail, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("fullyQualifiedName", obj.fullyQualifiedName)
    builder.addField("status", obj.status)
    builder.addField("duration", obj.duration)
    builder.endObject()
  }
}
}
