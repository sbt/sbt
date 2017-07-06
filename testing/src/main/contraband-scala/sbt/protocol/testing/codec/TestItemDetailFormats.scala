/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.testing.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait TestItemDetailFormats { self: sbt.internal.testing.StatusFormats with sjsonnew.BasicJsonProtocol =>
implicit lazy val TestItemDetailFormat: JsonFormat[sbt.protocol.testing.TestItemDetail] = new JsonFormat[sbt.protocol.testing.TestItemDetail] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.protocol.testing.TestItemDetail = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
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
