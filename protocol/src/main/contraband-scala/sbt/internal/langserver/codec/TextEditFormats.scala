/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait TextEditFormats { self: sbt.internal.langserver.codec.RangeFormats with sjsonnew.BasicJsonProtocol =>
implicit lazy val TextEditFormat: JsonFormat[sbt.internal.langserver.TextEdit] = new JsonFormat[sbt.internal.langserver.TextEdit] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.langserver.TextEdit = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val range = unbuilder.readField[sbt.internal.langserver.Range]("range")
      val newText = unbuilder.readField[String]("newText")
      unbuilder.endObject()
      sbt.internal.langserver.TextEdit(range, newText)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.langserver.TextEdit, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("range", obj.range)
    builder.addField("newText", obj.newText)
    builder.endObject()
  }
}
}
