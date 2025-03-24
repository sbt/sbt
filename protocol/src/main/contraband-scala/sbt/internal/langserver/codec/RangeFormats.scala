/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait RangeFormats { self: sbt.internal.langserver.codec.PositionFormats & sjsonnew.BasicJsonProtocol =>
implicit lazy val RangeFormat: JsonFormat[sbt.internal.langserver.Range] = new JsonFormat[sbt.internal.langserver.Range] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.langserver.Range = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val start = unbuilder.readField[sbt.internal.langserver.Position]("start")
      val end = unbuilder.readField[sbt.internal.langserver.Position]("end")
      unbuilder.endObject()
      sbt.internal.langserver.Range(start, end)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.langserver.Range, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("start", obj.start)
    builder.addField("end", obj.end)
    builder.endObject()
  }
}
}
