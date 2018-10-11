/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait CompletionItemFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val CompletionItemFormat: JsonFormat[sbt.internal.langserver.CompletionItem] = new JsonFormat[sbt.internal.langserver.CompletionItem] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.langserver.CompletionItem = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val label = unbuilder.readField[String]("label")
      unbuilder.endObject()
      sbt.internal.langserver.CompletionItem(label)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.langserver.CompletionItem, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("label", obj.label)
    builder.endObject()
  }
}
}
