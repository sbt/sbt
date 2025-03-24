/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait LocationFormats { self: sbt.internal.bsp.codec.RangeFormats & sbt.internal.bsp.codec.PositionFormats & sjsonnew.BasicJsonProtocol =>
implicit lazy val LocationFormat: JsonFormat[sbt.internal.bsp.Location] = new JsonFormat[sbt.internal.bsp.Location] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.Location = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val uri = unbuilder.readField[String]("uri")
      val range = unbuilder.readField[sbt.internal.bsp.Range]("range")
      unbuilder.endObject()
      sbt.internal.bsp.Location(uri, range)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.Location, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("uri", obj.uri)
    builder.addField("range", obj.range)
    builder.endObject()
  }
}
}
