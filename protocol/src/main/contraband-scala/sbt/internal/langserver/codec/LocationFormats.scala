/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait LocationFormats { self: sbt.internal.langserver.codec.RangeFormats & sbt.internal.langserver.codec.PositionFormats & sjsonnew.BasicJsonProtocol =>
implicit lazy val LocationFormat: JsonFormat[sbt.internal.langserver.Location] = new JsonFormat[sbt.internal.langserver.Location] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.langserver.Location = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val uri = unbuilder.readField[String]("uri")
      val range = unbuilder.readField[sbt.internal.langserver.Range]("range")
      unbuilder.endObject()
      sbt.internal.langserver.Location(uri, range)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.langserver.Location, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("uri", obj.uri)
    builder.addField("range", obj.range)
    builder.endObject()
  }
}
}
