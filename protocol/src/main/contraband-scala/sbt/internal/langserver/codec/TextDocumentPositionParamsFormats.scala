/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait TextDocumentPositionParamsFormats { self: sbt.internal.langserver.codec.TextDocumentIdentifierFormats with sbt.internal.langserver.codec.PositionFormats with sjsonnew.BasicJsonProtocol =>
implicit lazy val TextDocumentPositionParamsFormat: JsonFormat[sbt.internal.langserver.TextDocumentPositionParams] = new JsonFormat[sbt.internal.langserver.TextDocumentPositionParams] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.langserver.TextDocumentPositionParams = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val textDocument = unbuilder.readField[sbt.internal.langserver.TextDocumentIdentifier]("textDocument")
      val position = unbuilder.readField[sbt.internal.langserver.Position]("position")
      unbuilder.endObject()
      sbt.internal.langserver.TextDocumentPositionParams(textDocument, position)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.langserver.TextDocumentPositionParams, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("textDocument", obj.textDocument)
    builder.addField("position", obj.position)
    builder.endObject()
  }
}
}
