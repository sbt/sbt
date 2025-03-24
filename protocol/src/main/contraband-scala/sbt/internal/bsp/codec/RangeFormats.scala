/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait RangeFormats { self: sbt.internal.bsp.codec.PositionFormats & sjsonnew.BasicJsonProtocol =>
implicit lazy val RangeFormat: JsonFormat[sbt.internal.bsp.Range] = new JsonFormat[sbt.internal.bsp.Range] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.Range = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val start = unbuilder.readField[sbt.internal.bsp.Position]("start")
      val end = unbuilder.readField[sbt.internal.bsp.Position]("end")
      unbuilder.endObject()
      sbt.internal.bsp.Range(start, end)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.Range, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("start", obj.start)
    builder.addField("end", obj.end)
    builder.endObject()
  }
}
}
