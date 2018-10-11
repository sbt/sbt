/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait CompletionParamsFormats { self: sbt.internal.langserver.codec.TextDocumentIdentifierFormats with sbt.internal.langserver.codec.PositionFormats with sbt.internal.langserver.codec.CompletionContextFormats with sjsonnew.BasicJsonProtocol =>
implicit lazy val CompletionParamsFormat: JsonFormat[sbt.internal.langserver.CompletionParams] = new JsonFormat[sbt.internal.langserver.CompletionParams] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.langserver.CompletionParams = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val textDocument = unbuilder.readField[sbt.internal.langserver.TextDocumentIdentifier]("textDocument")
      val position = unbuilder.readField[sbt.internal.langserver.Position]("position")
      val context = unbuilder.readField[Option[sbt.internal.langserver.CompletionContext]]("context")
      unbuilder.endObject()
      sbt.internal.langserver.CompletionParams(textDocument, position, context)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.langserver.CompletionParams, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("textDocument", obj.textDocument)
    builder.addField("position", obj.position)
    builder.addField("context", obj.context)
    builder.endObject()
  }
}
}
