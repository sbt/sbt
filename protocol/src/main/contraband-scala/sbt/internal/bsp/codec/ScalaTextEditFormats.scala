/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ScalaTextEditFormats { self: sbt.internal.bsp.codec.RangeFormats & sbt.internal.bsp.codec.PositionFormats & sjsonnew.BasicJsonProtocol =>
implicit lazy val ScalaTextEditFormat: JsonFormat[sbt.internal.bsp.ScalaTextEdit] = new JsonFormat[sbt.internal.bsp.ScalaTextEdit] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.ScalaTextEdit = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val range = unbuilder.readField[sbt.internal.bsp.Range]("range")
      val newText = unbuilder.readField[String]("newText")
      unbuilder.endObject()
      sbt.internal.bsp.ScalaTextEdit(range, newText)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.ScalaTextEdit, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("range", obj.range)
    builder.addField("newText", obj.newText)
    builder.endObject()
  }
}
}
