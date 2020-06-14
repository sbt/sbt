/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait CompletionListFormats { self: sbt.internal.langserver.codec.CompletionItemFormats with sjsonnew.BasicJsonProtocol =>
implicit lazy val CompletionListFormat: JsonFormat[sbt.internal.langserver.CompletionList] = new JsonFormat[sbt.internal.langserver.CompletionList] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.langserver.CompletionList = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val isIncomplete = unbuilder.readField[Boolean]("isIncomplete")
      val items = unbuilder.readField[Vector[sbt.internal.langserver.CompletionItem]]("items")
      unbuilder.endObject()
      sbt.internal.langserver.CompletionList(isIncomplete, items)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.langserver.CompletionList, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("isIncomplete", obj.isIncomplete)
    builder.addField("items", obj.items)
    builder.endObject()
  }
}
}
